// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.storage;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.cloudstack.api.InternalIdentity;

@Entity
@Table(name = "storage_pool_and_access_group_map")
public class StoragePoolAndAccessGroupMapVO implements InternalIdentity {

    protected StoragePoolAndAccessGroupMapVO() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "pool_id")
    private long poolId;

    @Column(name = "storage_access_group")
    private String storageAccessGroup;

    public StoragePoolAndAccessGroupMapVO(long poolId, String storageAccessGroup) {
        this.poolId = poolId;
        this.storageAccessGroup = storageAccessGroup;
    }

    @Override
    public long getId() {
        return this.id;
    }

    public long getPoolId() {
        return poolId;
    }

    public String getStorageAccessGroup() {
        return storageAccessGroup;
    }

}
