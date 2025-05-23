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

package org.apache.cloudstack.storage.datastore.api;

import java.util.List;

public class StoragePool {
    String id;
    String name;
    String mediaType;
    String protectionDomainId;
    String systemId;
    List<String> mdmAddresses;
    StoragePoolStatistics statistics;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getProtectionDomainId() {
        return protectionDomainId;
    }

    public void setProtectionDomainId(String protectionDomainId) {
        this.protectionDomainId = protectionDomainId;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public List<String> getMdmAddresses() {
        return mdmAddresses;
    }

    public void setMdmAddresses(List<String> mdmAddresses) {
        this.mdmAddresses = mdmAddresses;
    }

    public StoragePoolStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(StoragePoolStatistics statistics) {
        this.statistics = statistics;
    }
}
