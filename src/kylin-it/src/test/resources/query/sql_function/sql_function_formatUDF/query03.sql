-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--    http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

--test to_char(timestamp,part)
select to_char(TEST_TIME_ENC,'YEAR'),
       to_char(TEST_TIME_ENC,'Y'),
       to_char(TEST_TIME_ENC,'y'),
       to_char(TEST_TIME_ENC,'MONTH'),
       to_char(TEST_TIME_ENC,'M'),
       to_char(TEST_TIME_ENC,'DAY'),
       to_char(TEST_TIME_ENC,'D'),
       to_char(TEST_TIME_ENC,'d'),
       to_char(TEST_TIME_ENC,'HOUR'),
       to_char(TEST_TIME_ENC,'H'),
       to_char(TEST_TIME_ENC,'h'),
       to_char(TEST_TIME_ENC,'MINUTE'),
       to_char(TEST_TIME_ENC,'MINUTES'),
       to_char(TEST_TIME_ENC,'m'),
       to_char(TEST_TIME_ENC,'SECOND'),
       to_char(TEST_TIME_ENC,'SECONDS'),
       to_char(TEST_TIME_ENC,'s')
from TEST_ORDER