/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.server.storage;

import java.util.List;
import java.util.Map;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.glowroot.storage.repo.TransactionTypeRepository;

import static com.google.common.base.Preconditions.checkNotNull;

public class TransactionTypeDao implements TransactionTypeRepository {

    private final Session session;

    private final PreparedStatement insertPS;

    public TransactionTypeDao(Session session) {
        this.session = session;

        session.execute("create table if not exists transaction_type (one int,"
                + " agent_rollup varchar, transaction_type varchar, primary key"
                + " (one, agent_rollup, transaction_type))");

        insertPS = session.prepare("insert into transaction_type (one, agent_rollup,"
                + " transaction_type) values (1, ?, ?)");
    }

    @Override
    public Map<String, List<String>> readTransactionTypes() {
        ResultSet results = session.execute(
                "select agent_rollup, transaction_type from transaction_type where one = 1");

        ImmutableMap.Builder<String, List<String>> builder = ImmutableMap.builder();
        String currAgentRollup = null;
        List<String> currTransactionTypes = Lists.newArrayList();
        for (Row row : results) {
            String agentRollup = checkNotNull(row.getString(0));
            String transactionType = checkNotNull(row.getString(1));
            if (currAgentRollup == null) {
                currAgentRollup = agentRollup;
            }
            if (!agentRollup.equals(currAgentRollup)) {
                builder.put(currAgentRollup, ImmutableList.copyOf(currTransactionTypes));
                currAgentRollup = agentRollup;
                currTransactionTypes = Lists.newArrayList();
            }
            currTransactionTypes.add(transactionType);
        }
        if (currAgentRollup != null) {
            builder.put(currAgentRollup, ImmutableList.copyOf(currTransactionTypes));
        }
        return builder.build();
    }

    @Override
    public void deleteAll(String agentRollup) throws Exception {
        // this is not currently supported (to avoid row key range query)
        throw new UnsupportedOperationException();
    }

    void updateLastCaptureTime(String agentRollup, String transactionType) {
        BoundStatement boundStatement = insertPS.bind();
        boundStatement.setString(0, agentRollup);
        boundStatement.setString(1, transactionType);
        session.execute(boundStatement);
    }
}
