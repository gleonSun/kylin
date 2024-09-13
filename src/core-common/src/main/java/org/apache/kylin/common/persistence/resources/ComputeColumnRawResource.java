/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kylin.common.persistence.resources;

import java.nio.charset.StandardCharsets;

import org.apache.kylin.common.persistence.RawResource;
import org.springframework.util.DigestUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ComputeColumnRawResource extends RawResource {
    @JsonProperty("project")
    private String project;

    @JsonProperty("columnName")
    private String columnName;

    @JsonProperty("tableIdentity")
    private String tableIdentity;

    @JsonProperty("tableAlias")
    private String tableAlias;

    @JsonProperty("expression")
    private String expression;

    @JsonProperty("innerExpression")
    private String innerExpression;

    @JsonProperty("datatype")
    private String datatype;

    @JsonProperty("referenceCount")
    private Integer referenceCount;

    @JsonProperty("comment")
    private String ccComment;
    
    private String expressionMd5;

    public String getExpressionMd5() {
        return DigestUtils.md5DigestAsHex(getExpression().getBytes(StandardCharsets.UTF_8));
    }
}