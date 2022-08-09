--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
SELECT "Z_PROVDASH_QPM_SCORE"."MEASURE_CODE" AS "MEASURE_CODE",
"Z_PROVDASH_QPM_SCORE"."MEASURE_DESC" AS "MEASURE_DESC",
AVG("Z_PROVDASH_QPM_SCORE"."BENCHMARK_RATE") AS "avg_BENCHMARK_RATE_ok",
AVG(("Z_PROVDASH_QPM_SCORE"."NPI_RATE" - "Z_PROVDASH_QPM_SCORE"."BENCHMARK_RATE")) AS "avg_Calculation_404761059420499972_ok",
AVG("Z_PROVDASH_QPM_SCORE"."NPI_RATE") AS "avg_NPI_RATE_ok"
FROM "POPHEALTH_ANALYTICS"."Z_PROVDASH_QPM_SCORE" "Z_PROVDASH_QPM_SCORE"
WHERE ("Z_PROVDASH_QPM_SCORE"."MEASURE_CODE" IN ('PQRS1', 'PQRS111', 'PQRS119', 'PQRS134', 'PQRS226'))
GROUP BY "Z_PROVDASH_QPM_SCORE"."MEASURE_CODE", "Z_PROVDASH_QPM_SCORE"."MEASURE_DESC"