package com.kylinolap.rest.security;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.acls.domain.AccessControlEntryImpl;
import org.springframework.security.acls.domain.AclAuthorizationStrategy;
import org.springframework.security.acls.domain.AclImpl;
import org.springframework.security.acls.domain.AuditLogger;
import org.springframework.security.acls.domain.GrantedAuthoritySid;
import org.springframework.security.acls.domain.ObjectIdentityImpl;
import org.springframework.security.acls.domain.PermissionFactory;
import org.springframework.security.acls.domain.PrincipalSid;
import org.springframework.security.acls.model.AccessControlEntry;
import org.springframework.security.acls.model.Acl;
import org.springframework.security.acls.model.AlreadyExistsException;
import org.springframework.security.acls.model.ChildrenExistException;
import org.springframework.security.acls.model.MutableAcl;
import org.springframework.security.acls.model.MutableAclService;
import org.springframework.security.acls.model.NotFoundException;
import org.springframework.security.acls.model.ObjectIdentity;
import org.springframework.security.acls.model.Sid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kylinolap.common.KylinConfig;
import com.kylinolap.common.persistence.HBaseConnection;

public class HbaseAclService implements MutableAclService {
    private static final Logger logger = LoggerFactory.getLogger(HbaseAclService.class);
    private static final Map<ObjectIdentity, Acl> aclCache = new ConcurrentHashMap<ObjectIdentity, Acl>();

    private static final String DEFAULT_TABLE_PREFIX = "kylin_metadata";
    private static final String USER_TABLE_NAME = "_user";
    private static final String ACL_INFO_FAMILY = "i";
    private static final String ACL_ACES_FAMILY = "a";
    private static final String ACL_INFO_FAMILY_TYPE_COLUMN = "t";
    private static final String ACL_INFO_FAMILY_OWNER_COLUMN = "o";
    private static final String ACL_INFO_FAMILY_PARENT_COLUMN = "p";
    private static final String ACL_INFO_FAMILY_ENTRY_INHERIT_COLUMN = "i";
    private String hbaseUrl = null;
    private String tableNameBase = null;
    private String userTableName = null;

    @Autowired
    protected PermissionFactory aclPermissionFactory;

    @Autowired
    protected AclAuthorizationStrategy aclAuthorizationStrategy;

    @Autowired
    protected AuditLogger auditLogger;

    public HbaseAclService() {
        String metadataUrl = KylinConfig.getInstanceFromEnv().getMetadataUrl();
        // split TABLE@HBASE_URL
        int cut = metadataUrl.indexOf('@');
        tableNameBase = cut < 0 ? DEFAULT_TABLE_PREFIX : metadataUrl.substring(0, cut);
        hbaseUrl = cut < 0 ? metadataUrl : metadataUrl.substring(cut + 1);
        userTableName = tableNameBase + USER_TABLE_NAME;
    }

    @Override
    public List<ObjectIdentity> findChildren(ObjectIdentity parentIdentity) {
        List<ObjectIdentity> oids = new ArrayList<ObjectIdentity>();
        HTableInterface htable = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);

            Scan scan = new Scan();
            Serializer<DomainObjectInfo> domainObjSerializer = new Serializer<DomainObjectInfo>(DomainObjectInfo.class);
            SingleColumnValueFilter parentFilter = new SingleColumnValueFilter(Bytes.toBytes(ACL_INFO_FAMILY), Bytes.toBytes(ACL_INFO_FAMILY_PARENT_COLUMN), CompareOp.EQUAL, domainObjSerializer.serialize(new DomainObjectInfo(parentIdentity)));
            scan.setFilter(parentFilter);
            
            ResultScanner scanner = htable.getScanner(scan);
            for (Result result = scanner.next(); result != null; result = scanner.next()) {
                String id = String.valueOf(result.getRow());
                String type = Bytes.toString(result.getValue(Bytes.toBytes(ACL_INFO_FAMILY), Bytes.toBytes(ACL_INFO_FAMILY_OWNER_COLUMN)));
                
                oids.add(new ObjectIdentityImpl(type, id));
            }
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(htable);
        }

        return oids;
    }

    @Override
    public Acl readAclById(ObjectIdentity object) throws NotFoundException {
        Map<ObjectIdentity, Acl> aclsMap = readAclsById(Arrays.asList(object), null);
        Assert.isTrue(aclsMap.containsKey(object), "There should have been an Acl entry for ObjectIdentity " + object);

        return aclsMap.get(object);
    }

    @Override
    public Acl readAclById(ObjectIdentity object, List<Sid> sids) throws NotFoundException {
        Map<ObjectIdentity, Acl> aclsMap = readAclsById(Arrays.asList(object), sids);
        Assert.isTrue(aclsMap.containsKey(object), "There should have been an Acl entry for ObjectIdentity " + object);

        return aclsMap.get(object);
    }

    @Override
    public Map<ObjectIdentity, Acl> readAclsById(List<ObjectIdentity> objects) throws NotFoundException {
        return readAclsById(objects, null);
    }

    @Override
    public Map<ObjectIdentity, Acl> readAclsById(List<ObjectIdentity> objects, List<Sid> sids) throws NotFoundException {
        Map<ObjectIdentity, Acl> aclMaps = new HashMap<ObjectIdentity, Acl>();
        HTableInterface htable = null;
        Result result = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);

            for (ObjectIdentity object : objects) {
                if (aclCache.containsKey(object)) {
                    aclMaps.put(object, aclCache.get(object));
                } else {
                    result = htable.get(new Get(Bytes.toBytes(String.valueOf(object.getIdentifier()))));

                    if (!result.isEmpty()) {
                        AclImpl acl = new AclImpl(object, object.getIdentifier(), aclAuthorizationStrategy, auditLogger);

                        Serializer<SidInfo> sidSerializer = new Serializer<SidInfo>(SidInfo.class);
                        SidInfo owner = sidSerializer.deserialize(result.getValue(Bytes.toBytes(ACL_INFO_FAMILY), Bytes.toBytes(ACL_INFO_FAMILY_OWNER_COLUMN)));
                        acl.setOwner((null == owner) ? null : (owner.isPrincipal() ? new PrincipalSid(owner.getSid()) : new GrantedAuthoritySid(owner.getSid())));
                        acl.setEntriesInheriting(Bytes.toBoolean(result.getValue(Bytes.toBytes(ACL_INFO_FAMILY), Bytes.toBytes(ACL_INFO_FAMILY_ENTRY_INHERIT_COLUMN))));

                        Serializer<DomainObjectInfo> domainObjSerializer = new Serializer<DomainObjectInfo>(DomainObjectInfo.class);
                        DomainObjectInfo parentInfo = domainObjSerializer.deserialize(result.getValue(Bytes.toBytes(ACL_INFO_FAMILY), Bytes.toBytes(ACL_INFO_FAMILY_PARENT_COLUMN)));

                        if (null != parentInfo) {
                            ObjectIdentity parentObj = new ObjectIdentityImpl(parentInfo.getType(), parentInfo.getId());
                            readAclsById(Arrays.asList(parentObj), null);
                            acl.setParent(aclCache.get(parentObj));
                        }

                        Serializer<AceInfo[]> aceSerializer = new Serializer<AceInfo[]>(AceInfo[].class);
                        AceInfo[] aces = null;
                        if (null != sids) {
                            // Just return aces in sids
                            for (Sid sid : sids) {
                                String sidName = null;
                                if (sid instanceof PrincipalSid) {
                                    sidName = ((PrincipalSid) sid).getPrincipal();
                                } else if (sid instanceof GrantedAuthoritySid) {
                                    sidName = ((GrantedAuthoritySid) sid).getGrantedAuthority();
                                }

                                aces = aceSerializer.deserialize(result.getValue(Bytes.toBytes(ACL_ACES_FAMILY), Bytes.toBytes(sidName)));
                            }
                        } else {
                            NavigableMap<byte[], byte[]> familyMap = result.getFamilyMap(Bytes.toBytes(ACL_ACES_FAMILY));
                            for (byte[] qualifier : familyMap.keySet()) {
                                aces = aceSerializer.deserialize(familyMap.get(qualifier));
                            }
                        }

                        for (AceInfo aceInfo : aces) {
                            Sid sid = aceInfo.getSidInfo().isPrincipal() ? new PrincipalSid(owner.getSid()) : new GrantedAuthoritySid(owner.getSid());
                            AccessControlEntry ace = new AccessControlEntryImpl(null, acl, sid, aclPermissionFactory.buildFromMask(aceInfo.getPermissionMask()), false, false, false);
                            acl.getEntries().add(ace);
                        }

                        aclMaps.put(object, acl);
                        aclCache.put(object, acl);
                    }
                }
            }
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(htable);
        }

        return aclMaps;
    }

    @Override
    public MutableAcl createAcl(ObjectIdentity objectIdentity) throws AlreadyExistsException {
        Acl acl = readAclById(objectIdentity);
        if (null != acl) {
            throw new AlreadyExistsException("ACL of " + objectIdentity + " exists!");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        PrincipalSid sid = new PrincipalSid(auth);

        HTableInterface htable = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);
            Put put = new Put(Bytes.toBytes(String.valueOf(objectIdentity.getIdentifier())));
            put.add(Bytes.toBytes(ACL_INFO_FAMILY), Bytes.toBytes(ACL_INFO_FAMILY_TYPE_COLUMN), Bytes.toBytes(objectIdentity.getType()));

            Serializer<SidInfo> sidSerializer = new Serializer<SidInfo>(SidInfo.class);
            put.add(Bytes.toBytes(ACL_INFO_FAMILY), Bytes.toBytes(ACL_INFO_FAMILY_OWNER_COLUMN), sidSerializer.serialize(new SidInfo(sid)));

            put.add(Bytes.toBytes(ACL_INFO_FAMILY), Bytes.toBytes(ACL_INFO_FAMILY_ENTRY_INHERIT_COLUMN), Bytes.toBytes(true));

            htable.put(put);
            htable.flushCommits();
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(htable);
        }

        return (MutableAcl) readAclById(objectIdentity);
    }

    @Override
    public void deleteAcl(ObjectIdentity objectIdentity, boolean deleteChildren) throws ChildrenExistException {
        HTableInterface htable = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);
            Delete delete = new Delete(Bytes.toBytes(String.valueOf(objectIdentity.getIdentifier())));

            List<ObjectIdentity> children = findChildren(objectIdentity);
            if (!deleteChildren && children.size()>0){
                throw new ChildrenExistException("Children exists for " + objectIdentity);
            }
            
            for (ObjectIdentity oid: children){
                deleteAcl(oid, deleteChildren);
            }
            
            htable.delete(delete);
            htable.flushCommits();
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(htable);
        }
    }

    @Override
    public MutableAcl updateAcl(MutableAcl acl) throws NotFoundException {
        Acl oldAcl = readAclById(acl.getObjectIdentity());
        if (null == oldAcl) {
            throw new NotFoundException("ACL of " + acl.getObjectIdentity() + " not found!");
        }

        Serializer<AceInfo> aceSerializer = new Serializer<AceInfo>(AceInfo.class);
        HTableInterface htable = null;
        try {
            htable = HBaseConnection.get(hbaseUrl).getTable(userTableName);
            Delete delete = new Delete(Bytes.toBytes(String.valueOf(acl.getObjectIdentity().getIdentifier())));
            delete.deleteFamily(Bytes.toBytes(ACL_ACES_FAMILY));
            htable.delete(delete);

            Put put = new Put(Bytes.toBytes(String.valueOf(acl.getObjectIdentity().getIdentifier())));
            for (AccessControlEntry ace : acl.getEntries()) {
                AceInfo aceInfo = new AceInfo(ace);
                put.add(Bytes.toBytes(ACL_INFO_FAMILY), Bytes.toBytes(aceInfo.getSidInfo().getSid()), aceSerializer.serialize(aceInfo));
            }
            htable.put(put);

            htable.flushCommits();
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage(), e);
        } finally {
            IOUtils.closeQuietly(htable);
        }

        return (MutableAcl) readAclById(acl.getObjectIdentity());
    }

    protected static class DomainObjectInfo {
        private Serializable id;
        private String type;

        public DomainObjectInfo() {
        }

        public DomainObjectInfo(ObjectIdentity oid) {
            super();
            this.id = oid.getIdentifier();
            this.type = oid.getType();
        }

        public Serializable getId() {
            return id;
        }

        public void setId(Serializable id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    protected static class SidInfo {
        private String sid;
        private boolean isPrincipal;

        public SidInfo() {
        }

        public SidInfo(Sid sid) {
            if (sid instanceof PrincipalSid) {
                this.sid = ((PrincipalSid) sid).getPrincipal();
                this.isPrincipal = true;
            } else if (sid instanceof GrantedAuthoritySid) {
                this.sid = ((GrantedAuthoritySid) sid).getGrantedAuthority();
                this.isPrincipal = false;
            }
        }

        public String getSid() {
            return sid;
        }

        public void setSid(String sid) {
            this.sid = sid;
        }

        public boolean isPrincipal() {
            return isPrincipal;
        }

        public void setPrincipal(boolean isPrincipal) {
            this.isPrincipal = isPrincipal;
        }
    }

    protected static class AceInfo {
        private SidInfo sidInfo;
        private int permissionMask;

        public AceInfo() {
        }

        public AceInfo(AccessControlEntry ace) {
            super();
            this.sidInfo = new SidInfo(ace.getSid());
            this.permissionMask = ace.getPermission().getMask();
        }

        public SidInfo getSidInfo() {
            return sidInfo;
        }

        public void setSidInfo(SidInfo sidInfo) {
            this.sidInfo = sidInfo;
        }

        public int getPermissionMask() {
            return permissionMask;
        }

        public void setPermissionMask(int permissionMask) {
            this.permissionMask = permissionMask;
        }

    }

    public static class Serializer<T> {
        private final Class<T> type;

        public Serializer(Class<T> type) {
            this.type = type;
        }

        @SuppressWarnings("unchecked")
        public T deserialize(byte[] value) {
            if (null == value) {
                return null;
            }

            try {
                ObjectMapper mapper = new ObjectMapper();

                return (T) Arrays.asList(mapper.readValue(value, type));
            } catch (JsonParseException e) {
                logger.error(e.getLocalizedMessage(), e);
            } catch (JsonMappingException e) {
                logger.error(e.getLocalizedMessage(), e);
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage(), e);
            }

            return null;
        }

        public byte[] serialize(T obj) {
            if (null == obj) {
                return null;
            }

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(buf);

            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.writeValue(dout, obj);
                dout.close();
                buf.close();
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage(), e);
            }

            return buf.toByteArray();
        }
    }

}
