//
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
//

package com.cloud.agent.api;

import com.cloud.host.Host;
import org.apache.cloudstack.command.CommandInfo;

public class PingCommand extends Command {
    Host.Type hostType;
    long hostId;
    boolean outOfBand;
    @LogLevel(LogLevel.Log4jLevel.Trace)
    private CommandInfo[] commandInfos = new CommandInfo[] {};

    protected PingCommand() {
    }

    public PingCommand(Host.Type type, long id) {
        hostType = type;
        hostId = id;
    }

    public PingCommand(Host.Type type, long id, boolean oob) {
        hostType = type;
        hostId = id;
        outOfBand = oob;
    }

    public Host.Type getHostType() {
        return hostType;
    }

    public long getHostId() {
        return hostId;
    }

    public boolean getOutOfBand() { return outOfBand; }

    public void setOutOfBand(boolean oob) { this.outOfBand = oob; }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PingCommand)) return false;
        if (!super.equals(o)) return false;

        PingCommand that = (PingCommand) o;

        if (hostId != that.hostId) return false;
        if (hostType != that.hostType) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (hostType != null ? hostType.hashCode() : 0);
        result = 31 * result + (int) (hostId ^ (hostId >>> 32));
        return result;
    }

    public CommandInfo[] getCommandInfos() {
        return commandInfos;
    }

    public void setCommandInfos(CommandInfo[] commandInfos) {
        this.commandInfos = commandInfos;
    }
}
