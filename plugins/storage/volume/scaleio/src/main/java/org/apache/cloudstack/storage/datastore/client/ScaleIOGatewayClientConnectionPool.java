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

package org.apache.cloudstack.storage.datastore.client;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

import com.cloud.storage.StoragePool;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailVO;
import org.apache.cloudstack.storage.datastore.db.StoragePoolDetailsDao;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.cloud.storage.StorageManager;
import com.cloud.utils.crypt.DBEncryptionUtil;
import com.google.common.base.Preconditions;

public class ScaleIOGatewayClientConnectionPool {
    protected Logger logger = LogManager.getLogger(getClass());

    private ConcurrentHashMap<Long, ScaleIOGatewayClient> gatewayClients;

    private static final ScaleIOGatewayClientConnectionPool instance;

    static {
        instance = new ScaleIOGatewayClientConnectionPool();
    }

    public static ScaleIOGatewayClientConnectionPool getInstance() {
        return instance;
    }

    private ScaleIOGatewayClientConnectionPool() {
        gatewayClients = new ConcurrentHashMap<Long, ScaleIOGatewayClient>();
    }

    public ScaleIOGatewayClient getClient(StoragePool storagePool,
                                          StoragePoolDetailsDao storagePoolDetailsDao)
            throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
        return getClient(storagePool.getId(), storagePool.getUuid(), storagePoolDetailsDao);
    }


    public ScaleIOGatewayClient getClient(DataStore dataStore,
                                          StoragePoolDetailsDao storagePoolDetailsDao)
            throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException {
        return getClient(dataStore.getId(), dataStore.getUuid(), storagePoolDetailsDao);
    }


    private ScaleIOGatewayClient getClient(Long storagePoolId, String storagePoolUuid,
                                           StoragePoolDetailsDao storagePoolDetailsDao)
            throws NoSuchAlgorithmException, KeyManagementException, URISyntaxException {

        Preconditions.checkArgument(storagePoolId != null && storagePoolId > 0,
                "Invalid storage pool id");

        ScaleIOGatewayClient client = null;
        synchronized (gatewayClients) {
            client = gatewayClients.get(storagePoolId);
            if (client == null) {
                String url = null;
                StoragePoolDetailVO urlDetail  = storagePoolDetailsDao.findDetail(storagePoolId, ScaleIOGatewayClient.GATEWAY_API_ENDPOINT);
                if (urlDetail != null) {
                    url = urlDetail.getValue();
                }
                String username = null;
                StoragePoolDetailVO encryptedUsernameDetail = storagePoolDetailsDao.findDetail(storagePoolId, ScaleIOGatewayClient.GATEWAY_API_USERNAME);
                if (encryptedUsernameDetail != null) {
                    final String encryptedUsername = encryptedUsernameDetail.getValue();
                    username = DBEncryptionUtil.decrypt(encryptedUsername);
                }
                String password = null;
                StoragePoolDetailVO encryptedPasswordDetail = storagePoolDetailsDao.findDetail(storagePoolId, ScaleIOGatewayClient.GATEWAY_API_PASSWORD);
                if (encryptedPasswordDetail != null) {
                    final String encryptedPassword = encryptedPasswordDetail.getValue();
                    password = DBEncryptionUtil.decrypt(encryptedPassword);
                }
                final int clientTimeout = StorageManager.STORAGE_POOL_CLIENT_TIMEOUT.valueIn(storagePoolId);
                final int clientMaxConnections = StorageManager.STORAGE_POOL_CLIENT_MAX_CONNECTIONS.valueIn(storagePoolId);

                client = new ScaleIOGatewayClientImpl(url, username, password, false, clientTimeout, clientMaxConnections);
                gatewayClients.put(storagePoolId, client);
                logger.debug("Added gateway client for the storage pool [id: {}, uuid: {}]", storagePoolId, storagePoolUuid);
            }
        }

        return client;
    }

    public boolean removeClient(DataStore dataStore) {
        Preconditions.checkArgument(dataStore != null && dataStore.getId() > 0,
                "Invalid storage pool id");

        ScaleIOGatewayClient client = null;
        synchronized (gatewayClients) {
            client = gatewayClients.remove(dataStore.getId());
        }

        if (client != null) {
            logger.debug("Removed gateway client for the storage pool: {}", dataStore);
            return true;
        }

        return false;
    }
}
