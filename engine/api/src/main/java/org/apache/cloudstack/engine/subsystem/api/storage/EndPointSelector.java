/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.engine.subsystem.api.storage;

import com.cloud.hypervisor.Hypervisor;

import java.util.List;

public interface EndPointSelector {
    EndPoint select(DataObject srcData, DataObject destData);

    EndPoint select(DataObject srcData, DataObject destData, boolean encryptionSupportRequired);

    EndPoint select(DataObject srcData, DataObject destData, StorageAction action);

    EndPoint select(DataObject srcData, DataObject destData, StorageAction action, boolean encryptionSupportRequired);

    EndPoint select(DataObject object);

    EndPoint select(DataStore store);

    EndPoint select(DataObject object, boolean encryptionSupportRequired);

    EndPoint select(DataObject object, StorageAction action);

    EndPoint select(DataObject object, StorageAction action, boolean encryptionSupportRequired);

    EndPoint selectRandom(long zoneId, Hypervisor.HypervisorType hypervisorType);

    List<EndPoint> selectAll(DataStore store);

    List<EndPoint> findAllEndpointsForScope(DataStore store);

    EndPoint select(Scope scope, Long storeId);

    EndPoint select(DataStore store, String downloadUrl);

    EndPoint findSsvm(Long dcId);
}
