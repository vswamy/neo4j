/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.bolt.v1.runtime.internal;

import java.util.function.Supplier;

import org.neo4j.bolt.security.auth.Authentication;
import org.neo4j.bolt.v1.runtime.Session;
import org.neo4j.bolt.v1.runtime.Sessions;
import org.neo4j.kernel.NeoStoreDataSource;
import org.neo4j.kernel.api.bolt.SessionTracker;
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge;
import org.neo4j.kernel.impl.logging.LogService;
import org.neo4j.kernel.impl.query.QueryExecutionEngine;
import org.neo4j.kernel.impl.transaction.log.TransactionIdStore;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.lifecycle.LifeSupport;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.udc.UsageData;

/**
 * The runtime environment in which user statements are executed. This is not a thread safe class, it is expected
 * that a single worker thread will use this environment.
 */
public class StandardSessions extends LifecycleAdapter implements Sessions
{
    private final GraphDatabaseAPI gds;
    private final LifeSupport life = new LifeSupport();
    private final UsageData usageData;
    private final LogService logging;
    private final Authentication authentication;
    private final SessionTracker sessionTracker;
    private final NeoStoreDataSource neoStoreDataSource;
    private final ThreadToStatementContextBridge txBridge;

    private CypherStatementRunner statementRunner;

    public StandardSessions( GraphDatabaseAPI gds, UsageData usageData, LogService logging,
            ThreadToStatementContextBridge txBridge, Authentication authentication,
            NeoStoreDataSource neoStoreDataSource, SessionTracker sessionTracker )
    {
        this.gds = gds;
        this.usageData = usageData;
        this.logging = logging;
        this.txBridge = txBridge;
        this.authentication = authentication;
        this.sessionTracker = sessionTracker;
        this.neoStoreDataSource = neoStoreDataSource;
    }

    @Override
    public void init() throws Throwable
    {
        life.init();
    }

    @Override
    public void start() throws Throwable
    {
        QueryExecutionEngine queryExecutionEngine =
                gds.getDependencyResolver().resolveDependency( QueryExecutionEngine.class );
        statementRunner = new CypherStatementRunner( queryExecutionEngine );
        life.start();
    }

    @Override
    public void stop() throws Throwable
    {
        life.stop();
    }

    @Override
    public void shutdown() throws Throwable
    {
        life.shutdown();
    }

    @Override
    public Session newSession( String connectionDescriptor, boolean isEncrypted )
    {
        Supplier<TransactionIdStore> transactionIdStore =
                neoStoreDataSource.getDependencyResolver().provideDependency( TransactionIdStore.class );
        SessionStateMachine.SPI spi =
                new StandardStateMachineSPI( connectionDescriptor, usageData, gds, statementRunner, logging,
                        authentication, txBridge, transactionIdStore, sessionTracker );
        return new SessionStateMachine( spi );
    }
}
