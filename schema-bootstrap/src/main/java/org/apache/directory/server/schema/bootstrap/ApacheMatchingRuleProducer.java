/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.schema.bootstrap;


import javax.naming.NamingException;

import org.apache.directory.server.schema.bootstrap.ProducerTypeEnum;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;


/**
 * A producer of MatchingRule objects for the eve schema. 
 * Probably modified by hand from generated code
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class ApacheMatchingRuleProducer extends AbstractBootstrapProducer
{
    public ApacheMatchingRuleProducer()
    {
        super( ProducerTypeEnum.MATCHING_RULE_PRODUCER );
    }


    // ------------------------------------------------------------------------
    // BootstrapProducer Methods
    // ------------------------------------------------------------------------

    /**
     * @see BootstrapProducer#produce(Registries, org.apache.directory.server.schema.bootstrap.ProducerCallback)
     */
    public void produce( Registries registries, ProducerCallback cb ) throws NamingException
    {
        BootstrapMatchingRule mrule = null;
        
        mrule = new BootstrapMatchingRule( "1.3.6.1.4.1.18060.0.4.1.1.1", registries );
        mrule.setNames( new String[]
            { "exactDnAsStringMatch" } );
        mrule.setSyntaxOid( "1.3.6.1.4.1.1466.115.121.1.12" );
        cb.schemaObjectProduced( this, mrule.getOid(), mrule );

        mrule = new BootstrapMatchingRule( "1.3.6.1.4.1.18060.0.4.1.1.2", registries );
        mrule.setNames( new String[]
            { "bigIntegerMatch" } );
        mrule.setSyntaxOid( "1.3.6.1.4.1.1466.115.121.1.27" );
        cb.schemaObjectProduced( this, mrule.getOid(), mrule );

        mrule = new BootstrapMatchingRule( "1.3.6.1.4.1.18060.0.4.1.1.3", registries );
        mrule.setNames( new String[]
            { "jdbmStringMatch" } );
        mrule.setSyntaxOid( "1.3.6.1.4.1.1466.115.121.1.15" );
        cb.schemaObjectProduced( this, mrule.getOid(), mrule );

        // For uuidMatch 
        mrule = new BootstrapMatchingRule( "1.3.6.1.1.16.2", registries );
        mrule.setNames( new String[]
            { "uuidMatch" } );
        mrule.setSyntaxOid( "1.3.6.1.1.16.1" );
        cb.schemaObjectProduced( this, mrule.getOid(), mrule );

        // For uuidOrderingMatch 
        mrule = new BootstrapMatchingRule( "1.3.6.1.1.16.3", registries );
        mrule.setNames( new String[]
            { "uuidOrderingMatch" } );
        mrule.setSyntaxOid( "1.3.6.1.1.16.1" );
        cb.schemaObjectProduced( this, mrule.getOid(), mrule );
        
        // For CSNMatch 
        mrule = new BootstrapMatchingRule( SchemaConstants.CSN_MATCH_MR_OID, registries );
        mrule.setNames( new String[]
            { SchemaConstants.CSN_MATCH_MR } );
        mrule.setSyntaxOid( SchemaConstants.CSN_SYNTAX );
        cb.schemaObjectProduced( this, mrule.getOid(), mrule );

        // For CSNOrderingMatch 
        mrule = new BootstrapMatchingRule( SchemaConstants.CSN_ORDERING_MATCH_MR_OID, registries );
        mrule.setNames( new String[]
            { SchemaConstants.CSN_ORDERING_MATCH_MR } );
        mrule.setSyntaxOid( SchemaConstants.CSN_SYNTAX );
        cb.schemaObjectProduced( this, mrule.getOid(), mrule );

        // For CSNSidMatch 
        mrule = new BootstrapMatchingRule( SchemaConstants.CSN_SID_MATCH_MR_OID, registries );
        mrule.setNames( new String[]
            { SchemaConstants.CSN_SID_MATCH_MR } );
        mrule.setSyntaxOid( SchemaConstants.CSN_SID_SYNTAX );
        cb.schemaObjectProduced( this, mrule.getOid(), mrule );
    }
}
