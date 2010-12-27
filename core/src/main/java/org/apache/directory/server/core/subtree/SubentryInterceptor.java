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
package org.apache.directory.server.core.subtree;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.naming.directory.SearchControls;

import org.apache.directory.server.constants.ApacheSchemaConstants;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.DefaultCoreSession;
import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.LdapPrincipal;
import org.apache.directory.server.core.admin.AdministrativePointInterceptor;
import org.apache.directory.server.core.administrative.AccessControlAAP;
import org.apache.directory.server.core.administrative.AccessControlAdministrativePoint;
import org.apache.directory.server.core.administrative.AccessControlIAP;
import org.apache.directory.server.core.administrative.AccessControlSAP;
import org.apache.directory.server.core.administrative.AdministrativePoint;
import org.apache.directory.server.core.administrative.AdministrativeRoleEnum;
import org.apache.directory.server.core.administrative.CollectiveAttributeAAP;
import org.apache.directory.server.core.administrative.CollectiveAttributeAdministrativePoint;
import org.apache.directory.server.core.administrative.CollectiveAttributeIAP;
import org.apache.directory.server.core.administrative.CollectiveAttributeSAP;
import org.apache.directory.server.core.administrative.Subentry;
import org.apache.directory.server.core.administrative.SubschemaAAP;
import org.apache.directory.server.core.administrative.SubschemaAdministrativePoint;
import org.apache.directory.server.core.administrative.SubschemaSAP;
import org.apache.directory.server.core.administrative.TriggerExecutionAAP;
import org.apache.directory.server.core.administrative.TriggerExecutionAdministrativePoint;
import org.apache.directory.server.core.administrative.TriggerExecutionIAP;
import org.apache.directory.server.core.administrative.TriggerExecutionSAP;
import org.apache.directory.server.core.authn.AuthenticationInterceptor;
import org.apache.directory.server.core.authz.AciAuthorizationInterceptor;
import org.apache.directory.server.core.authz.DefaultAuthorizationInterceptor;
import org.apache.directory.server.core.collective.CollectiveAttributeInterceptor;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.event.EventInterceptor;
import org.apache.directory.server.core.exception.ExceptionInterceptor;
import org.apache.directory.server.core.filtering.EntryFilter;
import org.apache.directory.server.core.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.interceptor.BaseInterceptor;
import org.apache.directory.server.core.interceptor.Interceptor;
import org.apache.directory.server.core.interceptor.NextInterceptor;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.ModifyOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveAndRenameOperationContext;
import org.apache.directory.server.core.interceptor.context.MoveOperationContext;
import org.apache.directory.server.core.interceptor.context.OperationContext;
import org.apache.directory.server.core.interceptor.context.RenameOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.interceptor.context.SearchingOperationContext;
import org.apache.directory.server.core.normalization.NormalizationInterceptor;
import org.apache.directory.server.core.operational.OperationalAttributeInterceptor;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.core.schema.SchemaInterceptor;
import org.apache.directory.server.core.trigger.TriggerInterceptor;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.shared.ldap.codec.search.controls.subentries.SubentriesControl;
import org.apache.directory.shared.ldap.constants.AuthenticationLevel;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.entry.DefaultEntry;
import org.apache.directory.shared.ldap.entry.DefaultEntryAttribute;
import org.apache.directory.shared.ldap.entry.DefaultModification;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.entry.EntryAttribute;
import org.apache.directory.shared.ldap.entry.Modification;
import org.apache.directory.shared.ldap.entry.ModificationOperation;
import org.apache.directory.shared.ldap.entry.StringValue;
import org.apache.directory.shared.ldap.entry.Value;
import org.apache.directory.shared.ldap.exception.LdapException;
import org.apache.directory.shared.ldap.exception.LdapInvalidAttributeValueException;
import org.apache.directory.shared.ldap.exception.LdapOperationErrorException;
import org.apache.directory.shared.ldap.exception.LdapOperationException;
import org.apache.directory.shared.ldap.exception.LdapOtherException;
import org.apache.directory.shared.ldap.exception.LdapSchemaViolationException;
import org.apache.directory.shared.ldap.exception.LdapUnwillingToPerformException;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.SearchScope;
import org.apache.directory.shared.ldap.message.AliasDerefMode;
import org.apache.directory.shared.ldap.message.ResultCodeEnum;
import org.apache.directory.shared.ldap.name.DN;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.SchemaManager;
import org.apache.directory.shared.ldap.subtree.AdministrativeRole;
import org.apache.directory.shared.ldap.subtree.SubtreeSpecification;
import org.apache.directory.shared.ldap.subtree.SubtreeSpecificationParser;
import org.apache.directory.shared.ldap.util.StringTools;
import org.apache.directory.shared.ldap.util.tree.DnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The Subentry interceptor service which is responsible for filtering
 * out subentries on search operations and injecting operational attributes
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class SubentryInterceptor extends BaseInterceptor
{
    /** The logger for this class */
    private static final Logger LOG = LoggerFactory.getLogger( SubentryInterceptor.class );

    /** the subentry control OID */
    private static final String SUBENTRY_CONTROL = SubentriesControl.CONTROL_OID;

    /** The set of Subentry operational attributes */
    public static AttributeType[] SUBENTRY_OPATTRS;

    /** the hash mapping the DN of a subentry to its SubtreeSpecification/types */
    private final SubentryCache subentryCache = new SubentryCache();

    /** The SubTree specification parser instance */
    private SubtreeSpecificationParser ssParser;

    /** The Subtree evaluator instance */
    private SubtreeEvaluator evaluator;

    /** A reference to the nexus for direct backend operations */
    private PartitionNexus nexus;

    /** A reference to the DirectoryService instance */
    private DirectoryService directoryService;

    /** The SchemManager instance */
    private SchemaManager schemaManager;

    /** A reference to the ObjectClass AT */
    private static AttributeType OBJECT_CLASS_AT;

    /** A reference to the AdministrativeRole AT */
    private static AttributeType ADMINISTRATIVE_ROLE_AT;

    /** A reference to the SubtreeSpecification AT */
    private static AttributeType SUBTREE_SPECIFICATION_AT;

    /** A reference to the AccessControlSubentries AT */
    private static AttributeType ACCESS_CONTROL_SUBENTRIES_AT;

    /** A reference to the AccessControlSubentries AT */
    private static AttributeType SUBSCHEMA_SUBENTRY_AT;

    /** A reference to the CollectiveAttributeSubentries AT */
    private static AttributeType COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT;

    /** A reference to the EntryUUID AT */
    private static AttributeType ENTRY_UUID_AT;
    
    /** A reference to the TriggerExecutionSubentries AT */
    private static AttributeType TRIGGER_EXECUTION_SUBENTRIES_AT;
    
    /** A reference to the SeqNumber AT */
    private static AttributeType AP_SEQ_NUMBER_AT;

    /** An enum used for the entries update */
    private enum OperationEnum
    {
        ADD,
        REMOVE,
        REPLACE
    }
    
    /** The possible roles */
    private static final Set<String> ROLES = new HashSet<String>();

    // Initialize the ROLES field
    static
    {
        ROLES.add( SchemaConstants.AUTONOMOUS_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.AUTONOMOUS_AREA_OID );
        ROLES.add( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
        ROLES.add( SchemaConstants.ACCESS_CONTROL_INNER_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.ACCESS_CONTROL_INNER_AREA_OID );
        ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
        ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA_OID );
        ROLES.add( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA_OID );
        ROLES.add( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
        ROLES.add( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA.toLowerCase() );
        ROLES.add( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA_OID );
    }

    /** A Map to associate a role with it's OID */
    private static final Map<String, String> ROLES_OID = new HashMap<String, String>();

    // Initialize the roles/oid map
    static
    {
        ROLES_OID.put( SchemaConstants.AUTONOMOUS_AREA.toLowerCase(), SchemaConstants.AUTONOMOUS_AREA_OID );
        ROLES_OID.put( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA.toLowerCase(),
            SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
        ROLES_OID.put( SchemaConstants.ACCESS_CONTROL_INNER_AREA.toLowerCase(),
            SchemaConstants.ACCESS_CONTROL_INNER_AREA_OID );
        ROLES_OID.put( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA.toLowerCase(),
            SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
        ROLES_OID.put( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA.toLowerCase(),
            SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA_OID );
        ROLES_OID.put( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA.toLowerCase(),
            SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA_OID );
        ROLES_OID.put( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA.toLowerCase(),
            SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
        ROLES_OID.put( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA.toLowerCase(),
            SchemaConstants.TRIGGER_EXECUTION_INNER_AREA_OID );
    }

    /** The possible inner area roles */
    private static final Set<String> INNER_AREA_ROLES = new HashSet<String>();

    static
    {
        INNER_AREA_ROLES.add( SchemaConstants.ACCESS_CONTROL_INNER_AREA.toLowerCase() );
        INNER_AREA_ROLES.add( SchemaConstants.ACCESS_CONTROL_INNER_AREA_OID );
        INNER_AREA_ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA.toLowerCase() );
        INNER_AREA_ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA_OID );
        INNER_AREA_ROLES.add( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA.toLowerCase() );
        INNER_AREA_ROLES.add( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA_OID );
    }

    /** The possible specific area roles */
    private static final Set<String> SPECIFIC_AREA_ROLES = new HashSet<String>();

    static
    {
        SPECIFIC_AREA_ROLES.add( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA.toLowerCase() );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA.toLowerCase() );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA.toLowerCase() );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA_OID );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA.toLowerCase() );
        SPECIFIC_AREA_ROLES.add( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
    }

    /** A lock to guarantee the AP cache consistency */
    private ReentrantReadWriteLock mutex = new ReentrantReadWriteLock();

    /**
     * the set of interceptors we should *not* go through updating some operational attributes
     */
    private static final Collection<String> BYPASS_INTERCEPTORS;

    static
    {
        Set<String> c = new HashSet<String>();
        c.add( NormalizationInterceptor.class.getName() );
        c.add( AuthenticationInterceptor.class.getName() );
        c.add( AciAuthorizationInterceptor.class.getName() );
        c.add( AdministrativePointInterceptor.class.getName() );
        c.add( DefaultAuthorizationInterceptor.class.getName() );
        c.add( ExceptionInterceptor.class.getName() );
        c.add( OperationalAttributeInterceptor.class.getName() );
        c.add( SchemaInterceptor.class.getName() );
        c.add( SubentryInterceptor.class.getName() );
        c.add( CollectiveAttributeInterceptor.class.getName() );
        c.add( EventInterceptor.class.getName() );
        c.add( TriggerInterceptor.class.getName() );
        BYPASS_INTERCEPTORS = Collections.unmodifiableCollection( c );
    }
    
    //-------------------------------------------------------------------------------------------
    // Search filter methods
    //-------------------------------------------------------------------------------------------
    /**
     * SearchResultFilter used to filter out subentries based on objectClass values.
     */
    public class HideSubentriesFilter implements EntryFilter
    {
        public boolean accept( SearchingOperationContext searchContext, ClonedServerEntry entry ) throws Exception
        {
            // See if the requested entry is a subentry
            if ( subentryCache.hasSubentry( entry.getDn() ) )
            {
                return false;
            }

            // see if we can use objectclass if present
            return !entry.contains( OBJECT_CLASS_AT, SchemaConstants.SUBENTRY_OC );
        }
    }


    /**
     * SearchResultFilter used to filter out normal entries but shows subentries based on
     * objectClass values.
     */
    public class HideEntriesFilter implements EntryFilter
    {
        public boolean accept( SearchingOperationContext searchContext, ClonedServerEntry entry ) throws Exception
        {
            // See if the requested entry is a subentry
            if ( subentryCache.hasSubentry( entry.getDn() ) )
            {
                return true;
            }

            // see if we can use objectclass if present
            return entry.contains( OBJECT_CLASS_AT, SchemaConstants.SUBENTRY_OC );
        }
    }


    //-------------------------------------------------------------------------------------------
    // Interceptor initialization
    //-------------------------------------------------------------------------------------------
    /**
     * Initialize the Subentry Interceptor
     *
     * @param directoryService The DirectoryService instance
     */
    public void init( DirectoryService directoryService ) throws LdapException
    {
        super.init( directoryService );

        this.directoryService = directoryService;
        nexus = directoryService.getPartitionNexus();
        schemaManager = directoryService.getSchemaManager();

        // setup various attribute type values
        OBJECT_CLASS_AT = schemaManager.getAttributeType( SchemaConstants.OBJECT_CLASS_AT );
        ADMINISTRATIVE_ROLE_AT = schemaManager.getAttributeType( SchemaConstants.ADMINISTRATIVE_ROLE_AT );
        SUBTREE_SPECIFICATION_AT = schemaManager.getAttributeType( SchemaConstants.SUBTREE_SPECIFICATION_AT );
        ACCESS_CONTROL_SUBENTRIES_AT = schemaManager.getAttributeType( SchemaConstants.ACCESS_CONTROL_SUBENTRIES_AT );
        SUBSCHEMA_SUBENTRY_AT = schemaManager.getAttributeType( SchemaConstants.SUBSCHEMA_SUBENTRY_AT );
        COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT = schemaManager.getAttributeType( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT );
        TRIGGER_EXECUTION_SUBENTRIES_AT = schemaManager.getAttributeType( SchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_AT );
        ENTRY_UUID_AT = schemaManager.getAttributeType( SchemaConstants.ENTRY_UUID_AT );
        AP_SEQ_NUMBER_AT = schemaManager.getAttributeType( ApacheSchemaConstants.AP_SEQ_NUMBER_AT );

        SUBENTRY_OPATTRS = new AttributeType[]
            {
                ACCESS_CONTROL_SUBENTRIES_AT,
                SUBSCHEMA_SUBENTRY_AT,
                COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT,
                TRIGGER_EXECUTION_SUBENTRIES_AT
            };

        ssParser = new SubtreeSpecificationParser( schemaManager );
        evaluator = new SubtreeEvaluator( schemaManager );

        // prepare to find all subentries in all namingContexts
        Set<String> suffixes = nexus.listSuffixes();
        ExprNode filter = new EqualityNode<String>( OBJECT_CLASS_AT, new StringValue(
            SchemaConstants.SUBENTRY_OC ) );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setReturningAttributes( new String[]
            { SchemaConstants.SUBTREE_SPECIFICATION_AT, SchemaConstants.OBJECT_CLASS_AT } );

        DN adminDn = directoryService.getDNFactory().create( ServerDNConstants.ADMIN_SYSTEM_DN );

        // search each namingContext for subentries
        for ( String suffix : suffixes )
        {
            DN suffixDn = directoryService.getDNFactory().create( suffix );

            CoreSession adminSession = new DefaultCoreSession(
                new LdapPrincipal( adminDn, AuthenticationLevel.STRONG ), directoryService );

            SearchOperationContext searchOperationContext = new SearchOperationContext( adminSession, suffixDn, filter,
                controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );

            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            // Loop on all the found Subentries, parse the SubtreeSpecification
            // and store the subentry in the subrentry cache
            try
            {
                while ( subentries.next() )
                {
                    Entry subentry = subentries.get();
                    DN subentryDn = subentry.getDn();

                    String subtree = subentry.get( SUBTREE_SPECIFICATION_AT ).getString();
                    SubtreeSpecification ss;

                    try
                    {
                        ss = ssParser.parse( subtree );
                    }
                    catch ( Exception e )
                    {
                        LOG.warn( "Failed while parsing subtreeSpecification for " + subentryDn );
                        continue;
                    }

                    Subentry newSubentry = new Subentry();

                    newSubentry.setAdministrativeRoles( getSubentryAdminRoles( subentry ) );
                    newSubentry.setSubtreeSpecification( ss );

                    subentryCache.addSubentry( subentryDn, newSubentry );
                }

                subentries.close();
            }
            catch ( Exception e )
            {
                throw new LdapOperationException( e.getMessage() );
            }
        }
    }


    //-------------------------------------------------------------------------------------------
    // Helper methods
    //-------------------------------------------------------------------------------------------
    /**
     * Return the list of AdministrativeRole for a subentry. We only use Specific Area roles.
     */
    private Set<AdministrativeRole> getSubentryAdminRoles( Entry subentry ) throws LdapException
    {
        Set<AdministrativeRole> adminRoles = new HashSet<AdministrativeRole>();

        if ( subentry.hasObjectClass( SchemaConstants.ACCESS_CONTROL_SUBENTRY_OC ) )
        {
            adminRoles.add( AdministrativeRole.AccessControlSpecificArea );
        }

        if ( subentry.hasObjectClass( SchemaConstants.SUBSCHEMA_OC ) )
        {
            adminRoles.add( AdministrativeRole.SubSchemaSpecificArea );
        }

        if ( subentry.hasObjectClass( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRY_OC ) )
        {
            adminRoles.add( AdministrativeRole.CollectiveAttributeSpecificArea );
        }

        if ( subentry.hasObjectClass( ApacheSchemaConstants.TRIGGER_EXECUTION_SUBENTRY_OC ) )
        {
            adminRoles.add( AdministrativeRole.TriggerExecutionSpecificArea );
        }

        return adminRoles;
    }


    /**
     * Checks to see if subentries for the search and list operations should be
     * made visible based on the availability of the search request control
     *
     * @param invocation the invocation object to use for determining subentry visibility
     * @return true if subentries should be visible, false otherwise
     * @throws Exception if there are problems accessing request controls
     */
    private boolean isSubentryVisible( OperationContext opContext ) throws LdapException
    {
        if ( !opContext.hasRequestControls() )
        {
            return false;
        }

        // found the subentry request control so we return its value
        if ( opContext.hasRequestControl( SUBENTRY_CONTROL ) )
        {
            SubentriesControl subentriesControl = ( SubentriesControl ) opContext.getRequestControl( SUBENTRY_CONTROL );

            return subentriesControl.isVisible();
        }

        return false;
    }

    /**
     * Update all the entries under an AP adding the
     */
    private void updateEntries( OperationEnum operation, CoreSession session, DN subentryDn, DN apDn, SubtreeSpecification ss, DN baseDn, List<EntryAttribute> operationalAttributes  ) throws LdapException
    {
        ExprNode filter = new PresenceNode( OBJECT_CLASS_AT ); // (objectClass=*)
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setReturningAttributes( new String[]
            { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

        SearchOperationContext searchOperationContext = new SearchOperationContext( session,
            baseDn, filter, controls );
        searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );

        EntryFilteringCursor subentries = nexus.search( searchOperationContext );

        try
        {
            while ( subentries.next() )
            {
                Entry candidate = subentries.get();
                DN candidateDn = candidate.getDn();

                if ( evaluator.evaluate( ss, apDn, candidateDn, candidate ) )
                {
                    List<Modification> modifications = null;

                    switch ( operation )
                    {
                        case ADD :
                            modifications = getOperationalModsForAdd( candidate, operationalAttributes );
                            break;

                        case REMOVE :
                            modifications = getOperationalModsForRemove( subentryDn, candidate );
                            break;

                            /*
                        case REPLACE :
                            modifications = getOperationalModsForReplace( subentryDn, candidate );
                            break;
                            */
                    }

                    LOG.debug( "The entry {} has been evaluated to true for subentry {}", candidate.getDn(), subentryDn );
                    nexus.modify( new ModifyOperationContext( session, candidateDn, modifications ) );
                }
            }
        }
        catch ( Exception e )
        {
            throw new LdapOtherException( e.getMessage() );
        }
    }


    /**
     * Checks if the given DN is a namingContext
     */
    private boolean isNamingContext( DN dn ) throws LdapException
    {
        DN namingContext = nexus.findSuffix( dn );

        return dn.equals( namingContext );
    }


    /**
     * Check that a subentry has the same role than it's parent AP
     *
    private void checkAdministrativeRole( Entry entry, DN apDn ) throws LdapException
    {
        // The administrativeRole AT must exist and not be null
        EntryAttribute administrativeRole = administrationPoint.get( ADMINISTRATIVE_ROLE_AT );

        // check that administrativeRole has something valid in it for us
        if ( ( administrativeRole == null ) || ( administrativeRole.size() <= 0 ) )
        {
            LOG.error( "The entry on {} is not an AdministrativePoint", apDn );
            throw new LdapNoSuchAttributeException( I18n.err( I18n.ERR_306, apDn ) );
        }
    }


    /**
     * Get the SubtreeSpecification, parse it and stores it into the subentry
     */
    private void setSubtreeSpecification( Subentry subentry, Entry entry ) throws LdapException
    {
        String subtree = entry.get( SUBTREE_SPECIFICATION_AT ).getString();
        SubtreeSpecification ss;

        try
        {
            ss = ssParser.parse( subtree );
        }
        catch ( Exception e )
        {
            String msg = I18n.err( I18n.ERR_307, entry.getDn() );
            LOG.warn( msg );
            throw new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, msg );
        }

        subentry.setSubtreeSpecification( ss );
    }


    /**
     * Checks to see if an entry being renamed has a descendant that is an
     * administrative point.
     *
     * @param name the name of the entry which is used as the search base
     * @return true if name is an administrative point or one of its descendants
     * are, false otherwise
     * @throws Exception if there are errors while searching the directory
     */
    private boolean hasAdministrativeDescendant( OperationContext opContext, DN name ) throws LdapException
    {
        ExprNode filter = new PresenceNode( ADMINISTRATIVE_ROLE_AT );
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );

        SearchOperationContext searchOperationContext = new SearchOperationContext( opContext.getSession(), name,
            filter, controls );
        searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );

        EntryFilteringCursor aps = nexus.search( searchOperationContext );

        try
        {
            if ( aps.next() )
            {
                aps.close();
                return true;
            }
        }
        catch ( Exception e )
        {
            throw new LdapOperationException( e.getMessage() );
        }


        return false;
    }


    private List<Modification> getModsOnEntryRdnChange( DN oldName, DN newName, Entry entry ) throws LdapException
    {
        List<Modification> modifications = new ArrayList<Modification>();

        /*
         * There are two different situations warranting action.  First if
         * an ss evalutating to true with the old name no longer evalutates
         * to true with the new name.  This would be caused by specific chop
         * exclusions that effect the new name but did not effect the old
         * name. In this case we must remove subentry operational attribute
         * values associated with the dn of that subentry.
         *
         * In the second case an ss selects the entry with the new name when
         * it did not previously with the old name. Again this situation
         * would be caused by chop exclusions. In this case we must add subentry
         * operational attribute values with the dn of this subentry.
         */
        for ( DN subentryDn : subentryCache )
        {
            DN apDn = subentryDn.getParent();
            SubtreeSpecification ss = subentryCache.getSubentry( subentryDn ).getSubtreeSpecification();
            boolean isOldNameSelected = evaluator.evaluate( ss, apDn, oldName, entry );
            boolean isNewNameSelected = evaluator.evaluate( ss, apDn, newName, entry );

            if ( isOldNameSelected == isNewNameSelected )
            {
                continue;
            }

            // need to remove references to the subentry
            if ( isOldNameSelected && !isNewNameSelected )
            {
                for ( AttributeType operationalAttribute : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.REPLACE_ATTRIBUTE;
                    EntryAttribute opAttr = entry.get( operationalAttribute );

                    if ( opAttr != null )
                    {
                        opAttr = opAttr.clone();
                        opAttr.remove( subentryDn.getNormName() );

                        if ( opAttr.size() < 1 )
                        {
                            op = ModificationOperation.REMOVE_ATTRIBUTE;
                        }

                        modifications.add( new DefaultModification( op, opAttr ) );
                    }
                }
            }
            // need to add references to the subentry
            else if ( isNewNameSelected && !isOldNameSelected )
            {
                for ( AttributeType operationalAttribute : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.ADD_ATTRIBUTE;
                    EntryAttribute opAttr = new DefaultEntryAttribute( operationalAttribute );
                    opAttr.add( subentryDn.getNormName() );
                    modifications.add( new DefaultModification( op, opAttr ) );
                }
            }
        }

        return modifications;
    }
    
    
    /**
     * Check that the AT contains the AccessControl SAP role
     */
    private boolean hasAccessControlSpecificRole( EntryAttribute adminPoint )
    {
        return adminPoint.contains( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA ) ||
               adminPoint.contains( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
    }


    /**
     * Check that the AT contains the CollectiveAttribute SAP role
     */
    private boolean hasCollectiveAttributeSpecificRole( EntryAttribute adminPoint )
    {
        return adminPoint.contains( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA ) ||
               adminPoint.contains( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
    }


    /**
     * Check that the AT contains the TriggerExecution SAP role
     */
    private boolean hasTriggerExecutionSpecificRole( EntryAttribute adminPoint )
    {
        return adminPoint.contains( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA ) ||
               adminPoint.contains( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
    }


    /**
     * Check that the AT contains the SubSchema SAP role
     */
    private boolean hasSubSchemaSpecificRole( EntryAttribute adminPoint )
    {
        return adminPoint.contains( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA ) ||
               adminPoint.contains( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA_OID );
    }


    /**
     * Tells if the role is an AC IAP
     */
    private boolean isAccessControlInnerRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.ACCESS_CONTROL_INNER_AREA ) ||
               role.equals( SchemaConstants.ACCESS_CONTROL_INNER_AREA_OID );
    }


    /**
     * Tells if the role is an AC SAP
     */
    private boolean isAccessControlSpecificRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA ) ||
               role.equals( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA_OID );
    }


    /**
     * Tells if the role is a CA IAP
     */
    private boolean isCollectiveAttributeInnerRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA ) ||
               role.equals( SchemaConstants.COLLECTIVE_ATTRIBUTE_INNER_AREA_OID );
    }


    /**
     * Tells if the role is a CA SAP
     */
    private boolean isCollectiveAttributeSpecificRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA ) ||
               role.equals( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA_OID );
    }


    /**
     * Tells if the role is a TE IAP
     */
    private boolean isTriggerExecutionInnerRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA ) ||
               role.equals( SchemaConstants.TRIGGER_EXECUTION_INNER_AREA_OID );
    }


    /**
     * Tells if the role is a TE SAP
     */
    private boolean isTriggerExecutionSpecificRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA ) ||
               role.equals( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA_OID );
    }


    /**
     * Tells if the role is a SS SAP
     */
    private boolean isSubschemaSpecficRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA ) ||
               role.equals( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA_OID );
    }


    /**
     * Tells if the role is an AAP
     */
    private boolean isAutonomousAreaRole( String role )
    {
        return role.equalsIgnoreCase( SchemaConstants.AUTONOMOUS_AREA ) ||
               role.equals( SchemaConstants.AUTONOMOUS_AREA_OID );
    }


    /**
     * Tells if the Administrative Point role is an AAP
     */
    private boolean isAAP( EntryAttribute adminPoint )
    {
        return ( adminPoint.contains( SchemaConstants.AUTONOMOUS_AREA ) || adminPoint
            .contains( SchemaConstants.AUTONOMOUS_AREA_OID ) );
    }


    /**
     * Check that we don't have an IAP and a SAP with the same family
     */
    private void checkInnerSpecificMix( String role, EntryAttribute adminPoint ) throws LdapUnwillingToPerformException
    {
        if ( isAccessControlInnerRole( role ) )
        {
            if ( hasAccessControlSpecificRole( adminPoint ) )
            {
                // This is inconsistent
                String message = "Cannot add a specific Administrative Point and the same"
                    + " inner Administrative point at the same time : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
            else
            {
                return;
            }
        }

        if ( isCollectiveAttributeInnerRole( role ) )
        {
            if ( hasCollectiveAttributeSpecificRole( adminPoint ) )
            {
                // This is inconsistent
                String message = "Cannot add a specific Administrative Point and the same"
                    + " inner Administrative point at the same time : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
            else
            {
                return;
            }
        }

        if ( isTriggerExecutionInnerRole( role ) )
        {
            if ( hasTriggerExecutionSpecificRole( adminPoint ) )
            {
                // This is inconsistent
                String message = "Cannot add a specific Administrative Point and the same"
                    + " inner Administrative point at the same time : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
            else
            {
                return;
            }
        }
    }


    private boolean isIAP( String role )
    {
        return INNER_AREA_ROLES.contains( role );
    }


    /**
     * Check that the IAPs (if any) have a parent. We will check for each kind or role :
     * AC, CA and TE.
     */
    private void checkIAPHasParent( String role, EntryAttribute adminPoint, DN dn )
        throws LdapUnwillingToPerformException
    {
        // Check for the AC role
        if ( isAccessControlInnerRole( role ) )
        {
            DnNode<AdministrativePoint> acCache = directoryService.getAccessControlAPCache();
            
            DnNode<AdministrativePoint> parent =  acCache.getNode( dn );
            
            if ( parent == null )
            {
                // We don't have any AC administrativePoint in the tree, this is an error
                String message = "Cannot add an IAP with no parent : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
        }
        else if ( isCollectiveAttributeInnerRole( role ) )
        {
            DnNode<AdministrativePoint> caCache = directoryService.getCollectiveAttributeAPCache();
            
            boolean hasAP = caCache.hasParentElement( dn );
            
            if ( !hasAP )
            {
                // We don't have any AC administrativePoint in the tree, this is an error
                String message = "Cannot add an IAP with no parent : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
        }
        else if ( isTriggerExecutionInnerRole( role ) )
        {
            DnNode<AdministrativePoint> caCache = directoryService.getTriggerExecutionAPCache();
            
            DnNode<AdministrativePoint> parent =  caCache.getNode( dn );
            
            if ( parent == null )
            {
                // We don't have any AC administrativePoint in the tree, this is an error
                String message = "Cannot add an IAP with no parent : " + adminPoint;
                LOG.error( message );
                throw new LdapUnwillingToPerformException( message );
            }
        }
        else
        {
            // Wtf ? We *must* have an IAP here...
            String message = "This is not an IAP : " + role;
            LOG.error( message );
            throw new LdapUnwillingToPerformException( message );
        }
    }
    
    
    /**
     * Check if we can safely add a role. If it's an AAP, we have to be sure that
     * all the 4 SAPs are present.
     */
    private void checkAddRole( Value<?> role, EntryAttribute adminPoint, DN dn ) throws LdapException
    {
        String roleStr = StringTools.toLowerCase( StringTools.trim( role.getString() ) );

        // Check that the added AdministrativeRole is valid
        if ( !ROLES.contains( roleStr ) )
        {
            String message = "Cannot add the given role, it's not a valid one :" + role;
            LOG.error( message );
            throw new LdapUnwillingToPerformException( message );
        }

        // If we are trying to add an AAP, we have to check that 
        // all the SAP roles are present. If nit, we add them.
        int nbRoles = adminPoint.size();
        
        if ( isAutonomousAreaRole( roleStr ) )
        {
            nbRoles--;
            
            if ( !hasAccessControlSpecificRole( adminPoint ) )
            {
                adminPoint.add( SchemaConstants.ACCESS_CONTROL_SPECIFIC_AREA );
            }
            else
            {
                nbRoles--;
            }
            
            if ( !hasCollectiveAttributeSpecificRole( adminPoint ) )
            {
                adminPoint.add( SchemaConstants.COLLECTIVE_ATTRIBUTE_SPECIFIC_AREA );
            }
            else
            {
                nbRoles--;
            }
            
            if ( !hasTriggerExecutionSpecificRole( adminPoint ) )
            {
                adminPoint.add( SchemaConstants.TRIGGER_EXECUTION_SPECIFIC_AREA );
            }
            else
            {
                nbRoles--;
            }
            
            if ( !hasSubSchemaSpecificRole( adminPoint ) )
            {
                adminPoint.add( SchemaConstants.SUB_SCHEMA_ADMIN_SPECIFIC_AREA );
            }
            else
            {
                nbRoles--;
            }
            
            if ( nbRoles != 0 )
            {
                // Check that we don't have any other role : we should have only 5 roles max
                String message = "Cannot add an Autonomous Administrative Point if we have some IAP roles : "
                    + adminPoint;
                LOG.error( message );
                
                throw new LdapUnwillingToPerformException( message );
            }
                
            // Fine, we have an AAP and the 4 SAPs
            return;
        }
        
        // check that we can't mix Inner and Specific areas
        checkInnerSpecificMix( roleStr, adminPoint );

        // Check that we don't add an IAP with no parent. The IAP must be added under
        // either a AAP, or a SAP/IAP within the same family
        if ( isIAP( roleStr ) )
        {
            checkIAPHasParent( roleStr, adminPoint, dn );
        }
    }


    /**
     * Check if an AP being added is valid or not :
     * - it's not under a subentry
     * - it cannot be a subentry
     * - the roles must be consistent (ie, AAP is coming with the 4 SAPs, we can't have a SAP and a IAP for the same role)
     * - it can't be the rootDSE or a NamingContext
     */
    private boolean checkIsValidAP( Entry entry ) throws LdapException
    {
        DN dn = entry.getDn();
        
        // Not rootDSE nor a namingContext
        if ( dn.isRootDSE() || isNamingContext( dn ) )
        {
            return false;
        }
        
        // Not a subentry
        if ( entry.hasObjectClass( SchemaConstants.SUBENTRY_OC ) )
        {
            return false;
        }
        
        DN parentDN = dn.getParent();
        
        // The parent is not a subentry
        if ( subentryCache.getSubentry( parentDN ) != null )
        {
            return false;
        }
        
        // Check the roles
        EntryAttribute adminPoint = entry.get( ADMINISTRATIVE_ROLE_AT );

        for ( Value<?> role : adminPoint )
        {
            checkAddRole( role, adminPoint, dn );
        }

        return true;
    }

    
    // -----------------------------------------------------------------------
    // Methods dealing with subentry modification
    // -----------------------------------------------------------------------

    private Set<AdministrativeRole> getSubentryTypes( Entry entry, List<Modification> mods ) throws LdapException
    {
        EntryAttribute ocFinalState = entry.get( OBJECT_CLASS_AT ).clone();

        for ( Modification mod : mods )
        {
            if ( mod.getAttribute().getId().equalsIgnoreCase( SchemaConstants.OBJECT_CLASS_AT ) ||
                 mod.getAttribute().getId().equalsIgnoreCase( SchemaConstants.OBJECT_CLASS_AT_OID ) )
            {
                switch ( mod.getOperation() )
                {
                    case ADD_ATTRIBUTE:
                        for ( Value<?> value : mod.getAttribute() )
                        {
                            ocFinalState.add( value.getString() );
                        }

                        break;

                    case REMOVE_ATTRIBUTE:
                        for ( Value<?> value : mod.getAttribute() )
                        {
                            ocFinalState.remove( value.getString() );
                        }

                        break;

                    case REPLACE_ATTRIBUTE:
                        ocFinalState = mod.getAttribute();
                        break;
                }
            }
        }

        Entry attrs = new DefaultEntry( schemaManager, DN.EMPTY_DN );
        attrs.put( ocFinalState );
        return getSubentryAdminRoles( attrs );
    }


    /**
     * Update the list of modifications with a modification associated with a specific
     * role, if it's requested.
     */
    private void getOperationalModForReplace( boolean hasRole, AttributeType attributeType, Entry entry, DN oldDn, DN newDn, List<Modification> modifications )
    {
        String oldDnStr = oldDn.getNormName();
        String newDnStr = newDn.getNormName();

        if ( hasRole )
        {
            EntryAttribute operational = entry.get( attributeType ).clone();

            if ( operational == null )
            {
                operational = new DefaultEntryAttribute( attributeType, newDnStr );
            }
            else
            {
                operational.remove( oldDnStr );
                operational.add( newDnStr );
            }

            modifications.add( new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE, operational ) );
        }
    }


    /**
     * Get the list of modifications to be applied on an entry to inject the operational attributes
     * associated with the administrative roles.
     */
    private List<Modification> getOperationalModsForReplace( DN oldDn, DN newDn, Subentry subentry, Entry entry )
        throws Exception
    {
        List<Modification> modifications = new ArrayList<Modification>();

        getOperationalModForReplace( subentry.isAccessControlAdminRole(), ACCESS_CONTROL_SUBENTRIES_AT, entry, oldDn, newDn, modifications );
        getOperationalModForReplace( subentry.isSchemaAdminRole(), SUBSCHEMA_SUBENTRY_AT, entry, oldDn, newDn, modifications );
        getOperationalModForReplace( subentry.isCollectiveAdminRole(), COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT, entry, oldDn, newDn, modifications );
        getOperationalModForReplace( subentry.isTriggersAdminRole(), TRIGGER_EXECUTION_SUBENTRIES_AT, entry, oldDn, newDn, modifications );

        return modifications;
    }


    /**
     * Gets the subschema operational attributes to be added to or removed from
     * an entry selected by a subentry's subtreeSpecification.
     */
    private List<EntryAttribute> getSubentryOperationalAttributes( DN dn, Subentry subentry ) throws LdapException
    {
        List<EntryAttribute> attributes = new ArrayList<EntryAttribute>();

        if ( subentry.isAccessControlAdminRole() )
        {
            EntryAttribute accessControlSubentries = new DefaultEntryAttribute( ACCESS_CONTROL_SUBENTRIES_AT, dn.getNormName() );
            attributes.add( accessControlSubentries );
        }

        if ( subentry.isSchemaAdminRole() )
        {
            EntryAttribute subschemaSubentry = new DefaultEntryAttribute( SUBSCHEMA_SUBENTRY_AT, dn.getNormName() );
            attributes.add( subschemaSubentry );
        }

        if ( subentry.isCollectiveAdminRole() )
        {
            EntryAttribute collectiveAttributeSubentries = new DefaultEntryAttribute( COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT, dn.getNormName() );
            attributes.add( collectiveAttributeSubentries );
        }

        if ( subentry.isTriggersAdminRole() )
        {
            EntryAttribute triggerExecutionSubentries = new DefaultEntryAttribute( TRIGGER_EXECUTION_SUBENTRIES_AT, dn.getNormName() );
            attributes.add( triggerExecutionSubentries );
        }

        return attributes;
    }


    /**
     * Calculates the subentry operational attributes to remove from a candidate
     * entry selected by a subtreeSpecification.  When we remove a subentry we
     * must remove the operational attributes in the entries that were once selected
     * by the subtree specification of that subentry.  To do so we must perform
     * a modify operation with the set of modifications to perform.  This method
     * calculates those modifications.
     *
     * @param subentryDn the distinguished name of the subentry
     * @param candidate the candidate entry to removed from the
     * @return the set of modifications required to remove an entry's reference to
     * a subentry
     */
    private List<Modification> getOperationalModsForRemove( DN subentryDn, Entry candidate ) throws LdapException
    {
        List<Modification> modifications = new ArrayList<Modification>();
        String dn = subentryDn.getNormName();

        for ( AttributeType operationalAttribute : SUBENTRY_OPATTRS )
        {
            EntryAttribute opAttr = candidate.get( operationalAttribute );

            if ( ( opAttr != null ) && opAttr.contains( dn ) )
            {
                EntryAttribute attr = new DefaultEntryAttribute( operationalAttribute, dn );
                modifications.add( new DefaultModification( ModificationOperation.REMOVE_ATTRIBUTE, attr ) );
            }
        }

        return modifications;
    }


    /**
     * Calculates the subentry operational attributes to add or replace from
     * a candidate entry selected by a subtree specification.  When a subentry
     * is added or it's specification is modified some entries must have new
     * operational attributes added to it to point back to the associated
     * subentry.  To do so a modify operation must be performed on entries
     * selected by the subtree specification.  This method calculates the
     * modify operation to be performed on the entry.
     */
    private List<Modification> getOperationalModsForAdd( Entry entry, List<EntryAttribute> operationalAttributes ) throws LdapException
    {
        List<Modification> modifications = new ArrayList<Modification>();

        for ( EntryAttribute operationalAttribute : operationalAttributes )
        {
            EntryAttribute opAttrInEntry = entry.get( operationalAttribute.getAttributeType() );

            if ( ( opAttrInEntry != null ) && ( opAttrInEntry.size() > 0 ) )
            {
                EntryAttribute newOperationalAttribute = operationalAttribute.clone();

                for ( Value<?> value : opAttrInEntry )
                {
                    newOperationalAttribute.add( value );
                }

                modifications.add( new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE, newOperationalAttribute ) );
            }
            else
            {
                modifications.add( new DefaultModification( ModificationOperation.ADD_ATTRIBUTE, operationalAttribute ) );
            }
        }

        return modifications;
    }


    /**
     * Get the list of modification to apply to all the entries
     */
    private List<Modification> getModsOnEntryModification( DN name, Entry oldEntry, Entry newEntry ) throws LdapException
    {
        List<Modification> modList = new ArrayList<Modification>();

        for ( DN subentryDn : subentryCache )
        {
            DN apDn = subentryDn.getParent();
            SubtreeSpecification ss = subentryCache.getSubentry( subentryDn ).getSubtreeSpecification();
            boolean isOldEntrySelected = evaluator.evaluate( ss, apDn, name, oldEntry );
            boolean isNewEntrySelected = evaluator.evaluate( ss, apDn, name, newEntry );

            if ( isOldEntrySelected == isNewEntrySelected )
            {
                continue;
            }

            // need to remove references to the subentry
            if ( isOldEntrySelected && !isNewEntrySelected )
            {
                for ( AttributeType operationalAttribute : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.REPLACE_ATTRIBUTE;
                    EntryAttribute opAttr = oldEntry.get( operationalAttribute );

                    if ( opAttr != null )
                    {
                        opAttr = opAttr.clone();
                        opAttr.remove( subentryDn.getNormName() );

                        if ( opAttr.size() < 1 )
                        {
                            op = ModificationOperation.REMOVE_ATTRIBUTE;
                        }

                        modList.add( new DefaultModification( op, opAttr ) );
                    }
                }
            }
            // need to add references to the subentry
            else if ( isNewEntrySelected && !isOldEntrySelected )
            {
                for ( AttributeType operationalAttribute : SUBENTRY_OPATTRS )
                {
                    ModificationOperation op = ModificationOperation.ADD_ATTRIBUTE;
                    EntryAttribute opAttr = new DefaultEntryAttribute( operationalAttribute );
                    opAttr.add( subentryDn.getNormName() );
                    modList.add( new DefaultModification( op, opAttr ) );
                }
            }
        }

        return modList;
    }


    /**
     * Update the Operational Attribute with the reference to the subentry
     */
    private void setOperationalAttribute( Entry entry, DN subentryDn, AttributeType opAttr) throws LdapException
    {
        EntryAttribute operational = entry.get( opAttr );

        if ( operational == null )
        {
            operational = new DefaultEntryAttribute( opAttr );
            entry.put( operational );
        }

        operational.add( subentryDn.getNormName() );
    }


    /**
     * Get a read-lock on the AP cache.
     * No read operation can be done on the AP cache if this
     * method is not called before.
     */
    public void lockRead()
    {
        mutex.readLock().lock();
    }


    /**
     * Get a write-lock on the AP cache.
     * No write operation can be done on the apCache if this
     * method is not called before.
     */
    public void lockWrite()
    {
        mutex.writeLock().lock();
    }


    /**
     * Release the read-write lock on the AP cache.
     * This method must be called after having read or modified the
     * AP cache
     */
    public void unlock()
    {
        if ( mutex.isWriteLockedByCurrentThread() )
        {
            mutex.writeLock().unlock();
        }
        else
        {
            mutex.readLock().unlock();
        }
    }

    
    /**
     * Create the list of AP for a given entry.
     */
    private void createAdministrativePoints( EntryAttribute adminPoint, DN dn, String uuid, long seqNumber ) throws LdapException
    {
        if ( isAAP( adminPoint ) )
        {
            // The AC AAP
            AccessControlAdministrativePoint acAap = new AccessControlAAP( uuid, seqNumber );
            directoryService.getAccessControlAPCache().add( dn, acAap );

            // The CA AAP
            CollectiveAttributeAdministrativePoint caAap = new CollectiveAttributeAAP( uuid, seqNumber );
            directoryService.getCollectiveAttributeAPCache().add( dn, caAap );

            // The TE AAP
            TriggerExecutionAdministrativePoint teAap = new TriggerExecutionAAP( uuid, seqNumber );
            directoryService.getTriggerExecutionAPCache().add( dn, teAap );

            // The SS AAP
            SubschemaAdministrativePoint ssAap = new SubschemaAAP( uuid, seqNumber );
            directoryService.getSubschemaAPCache().add( dn, ssAap );

            // If it's an AAP, we can get out immediately
            return;
        }

        // Not an AAP
        for ( Value<?> value : adminPoint )
        {
            String role = value.getString();

            // Deal with AccessControl AP
            if ( isAccessControlSpecificRole( role ) )
            {
                AccessControlAdministrativePoint sap = new AccessControlSAP( uuid, seqNumber );
                directoryService.getAccessControlAPCache().add( dn, sap );

                continue;
            }

            if ( isAccessControlInnerRole( role ) )
            {
                AccessControlAdministrativePoint iap = new AccessControlIAP( uuid, seqNumber );
                directoryService.getAccessControlAPCache().add( dn, iap );

                continue;
            }

            // Deal with CollectiveAttribute AP
            if ( isCollectiveAttributeSpecificRole( role ) )
            {
                CollectiveAttributeAdministrativePoint sap = new CollectiveAttributeSAP( uuid, seqNumber );
                directoryService.getCollectiveAttributeAPCache().add( dn, sap );

                continue;
            }

            if ( isCollectiveAttributeInnerRole( role ) )
            {
                CollectiveAttributeAdministrativePoint iap = new CollectiveAttributeIAP( uuid, seqNumber );
                directoryService.getCollectiveAttributeAPCache().add( dn, iap );

                continue;
            }

            // Deal with SubSchema AP
            if ( isSubschemaSpecficRole( role ) )
            {
                SubschemaAdministrativePoint sap = new SubschemaSAP( uuid, seqNumber );
                directoryService.getSubschemaAPCache().add( dn, sap );

                continue;
            }

            // Deal with TriggerExecution AP
            if ( isTriggerExecutionSpecificRole( role ) )
            {
                TriggerExecutionAdministrativePoint sap = new TriggerExecutionSAP( uuid, seqNumber );
                directoryService.getTriggerExecutionAPCache().add( dn, sap );

                continue;
            }

            if ( isTriggerExecutionInnerRole( role ) )
            {
                TriggerExecutionAdministrativePoint iap = new TriggerExecutionIAP( uuid, seqNumber );
                directoryService.getTriggerExecutionAPCache().add( dn, iap );

                continue;
            }
        }

        return;
    }
    
    
    /**
     * Delete the list of AP for a given entry. We can update the cache for each role,
     * as if the AP doe snot have such a role, it won't do anythig anyway
     */
    private void deleteAdministrativePoints( EntryAttribute adminPoint, DN dn ) throws LdapException
    {
        // The AC SAP
        directoryService.getAccessControlAPCache().remove( dn );

        // The CA SAP
        directoryService.getCollectiveAttributeAPCache().remove( dn );

        // The TE SAP
        directoryService.getTriggerExecutionAPCache().remove( dn );

        // The SS SAP
        directoryService.getSubschemaAPCache().remove( dn );
        // If it's an AAP, we can get out immediately
        return;

        /*
        if ( isAAP( adminPoint ) )
        {
            // The AC AAP
            directoryService.getAccessControlAPCache().remove( dn );

            // The CA AAP
            directoryService.getCollectiveAttributeAPCache().remove( dn );

            // The TE AAP
            directoryService.getTriggerExecutionAPCache().remove( dn );

            // The SS AAP
            directoryService.getSubschemaAPCache().remove( dn );

            // If it's an AAP, we can get out immediately
            return;
        }

        // Not an AAP
        for ( Value<?> value : adminPoint )
        {
            String role = value.getString();

            // Deal with AccessControl AP
            if ( isAccessControlSpecificRole( role ) || isAccessControlInnerRole( role ) )
            {
                directoryService.getAccessControlAPCache().remove( dn );

                continue;
            }

            // Deal with CollectiveAttribute AP
            if ( isCollectiveAttributeSpecificRole( role ) || isCollectiveAttributeInnerRole( role ) )
            {
                directoryService.getCollectiveAttributeAPCache().remove( dn );

                continue;
            }

            // Deal with SubSchema AP
            if ( isSubschemaSpecficRole( role ) )
            {
                directoryService.getSubschemaAPCache().remove( dn );

                continue;
            }

            // Deal with TriggerExecution AP
            if ( isTriggerExecutionSpecificRole( role ) || isTriggerExecutionInnerRole( role ) )
            {
                directoryService.getTriggerExecutionAPCache().remove( dn );

                continue;
            }
        }

        return;
        */
    }
    
    
    /**
     * Get the AdministrativePoint associated with a subentry
     * @param apDn
     * @return
     */
    private AdministrativePoint getAdministrativePoint( DN apDn )
    {
        AdministrativePoint administrativePoint = directoryService.getAccessControlAPCache().getElement( apDn );
        
        if ( administrativePoint != null )
        {
            return administrativePoint;
        }
        
        administrativePoint = directoryService.getCollectiveAttributeAPCache().getElement( apDn );

        if ( administrativePoint != null )
        {
            return administrativePoint;
        }
        
        administrativePoint = directoryService.getTriggerExecutionAPCache().getElement( apDn );

        if ( administrativePoint != null )
        {
            return administrativePoint;
        }
        
        administrativePoint = directoryService.getSubschemaAPCache().getElement( apDn );

        if ( administrativePoint != null )
        {
            return administrativePoint;
        }
        
        return null;
    }
    
    
    /**
     * Tells if the subentry's roles point to an AP
     */
    private boolean isValidSubentry( Entry subentry, DN apDn )
    {
        boolean isValid = true;
        
        // Check that the ACAP exists if the AC subentry OC is present in the subentry
        if ( subentry.hasObjectClass( SchemaConstants.ACCESS_CONTROL_SUBENTRY_OC ) ||
             subentry.hasObjectClass( SchemaConstants.ACCESS_CONTROL_SUBENTRY_OC_OID ) )
        {
            isValid &= directoryService.getAccessControlAPCache().hasElement( apDn );
        }

        // Check that the CAAP exists if the CA subentry OC is present in the subentry
        if ( subentry.hasObjectClass( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRY_OC ) ||
             subentry.hasObjectClass( SchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRY_OC_OID ) )
        {
            isValid &= directoryService.getCollectiveAttributeAPCache().hasElement( apDn );
        }

        // Check that the TEAP exists if the TE subentry OC is present in the subentry
        if ( subentry.hasObjectClass( SchemaConstants.TRIGGER_EXECUTION_SUBENTRY_OC ) ||
             subentry.hasObjectClass( SchemaConstants.TRIGGER_EXECUTION_SUBENTRY_OC_OID ) )
        {
            isValid &= directoryService.getTriggerExecutionAPCache().hasElement( apDn );
        }
        

        // Check that the SSAP exists if the SS subentry OC is present in the subentry
        if ( subentry.hasObjectClass( SchemaConstants.SUBSCHEMA_OC ) ||
             subentry.hasObjectClass( SchemaConstants.SUBSCHEMA_OC_OID ) )
        {
            isValid &= directoryService.getSubschemaAPCache().hasElement( apDn );
        }
        
        return isValid;
    }
    
    
    /**
     * Inject a new seqNumber in an AP
     */
    private long updateSeqNumber( DN apDn ) throws LdapException
    {
        long seqNumber = directoryService.getNewApSeqNumber();
        
        EntryAttribute newSeqNumber = new DefaultEntryAttribute( AP_SEQ_NUMBER_AT, Long.toString( seqNumber ) );
        
        Modification modification = new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE, newSeqNumber );
        List<Modification> modifications = new ArrayList<Modification>();
        modifications.add( modification );
        
        ModifyOperationContext modCtx = new ModifyOperationContext( directoryService.getAdminSession() );
        modCtx.setByPassed( BYPASS_INTERCEPTORS );
        modCtx.setDn( apDn );
        modCtx.setModItems( modifications );
        
        directoryService.getOperationManager().modify( modCtx );
        
        return seqNumber;
    }
    
    
    /**
     * Process the addition of a standard entry
     */
    private void processAddEntry( AdministrativeRoleEnum role, Entry entry ) throws LdapException
    {
        DN dn = entry.getDn();
        
        // first get the parent AP for this entry
        switch ( role )
        {
            case AccessControl :
                DnNode<AdministrativePoint> nodeAP = directoryService.getAccessControlAPCache().getParentWithElement( dn );
                
                if ( nodeAP != null )
                {
                    AdministrativePoint parentAP = nodeAP.getElement();
    
                    if ( parentAP != null )
                    {
                        // Update the entry seqNumber for AC
                        entry.put( ApacheSchemaConstants.ACCESS_CONTROL_SEQ_NUMBER_AT, Long.toString( parentAP.getSeqNumber() ) );
                        
                        // This entry has a AccessControl AP parent.
                        Set<Subentry> subentries = parentAP.getSubentries();
    
                        if ( subentries != null )
                        {
                            for ( Subentry subentry : subentries )
                            {
                                SubtreeSpecification ss = subentry.getSubtreeSpecification();
        
                                // Now, evaluate the entry wrt the subentry ss
                                // and inject a ref to the subentry if it evaluates to true
                                if ( evaluator.evaluate( ss, nodeAP.getDn(), dn, entry ) )
                                {
                                    // Point to the subentry UUID
                                    entry.add( ApacheSchemaConstants.ACCESS_CONTROL_SUBENTRIES_UUID_AT, subentry.getUuid() );
                                }
                            }
                        }
                    }
                }

            case CollectiveAttribute :
                nodeAP = directoryService.getCollectiveAttributeAPCache().getParentWithElement( dn );

                if ( nodeAP != null )
                {
                    AdministrativePoint parentAP = nodeAP.getElement();
    
                    if ( parentAP != null )
                    {
                        // Update the entry seqNumber for CA
                        entry.put( ApacheSchemaConstants.ACCESS_CONTROL_SEQ_NUMBER_AT, Long.toString( parentAP.getSeqNumber() ) );
                        
                        // This entry has a CollectiveAttribute AP parent.
                        Set<Subentry> subentries = parentAP.getSubentries();
    
                        if ( subentries != null )
                        {
                            for ( Subentry subentry : subentries )
                            {
                                SubtreeSpecification ss = subentry.getSubtreeSpecification();
        
                                // Now, evaluate the entry wrt the subentry ss
                                // and inject a ref to the subentry if it evaluates to true
                                if ( evaluator.evaluate( ss, nodeAP.getDn(), dn, entry ) )
                                {
                                    // Point to the subentry UUID
                                    entry.add( ApacheSchemaConstants.COLLECTIVE_ATTRIBUTE_SUBENTRIES_UUID_AT, subentry.getUuid() );
                                }
                            }
                        }
                    }
                }

            case SubSchema :
                nodeAP = directoryService.getSubschemaAPCache().getParentWithElement( dn );

                if ( nodeAP != null )
                {
                    AdministrativePoint parentAP = nodeAP.getElement();
    
                    if ( parentAP != null )
                    {
                        // This entry has a Subschema AP parent.
                        // Update the entry seqNumber for CA
                        entry.put( ApacheSchemaConstants.ACCESS_CONTROL_SEQ_NUMBER_AT, Long.toString( parentAP.getSeqNumber() ) );
                        
                        // This entry has a CollectiveAttribute AP parent.
                        Set<Subentry> subentries = parentAP.getSubentries();
    
                        if ( subentries != null )
                        {
                            for ( Subentry subentry : subentries )
                            {
                                SubtreeSpecification ss = subentry.getSubtreeSpecification();
        
                                // Now, evaluate the entry wrt the subentry ss
                                // and inject a ref to the subentry if it evaluates to true
                                if ( evaluator.evaluate( ss, nodeAP.getDn(), dn, entry ) )
                                {
                                    // Point to the subentry UUID
                                    entry.add( ApacheSchemaConstants.SUB_SCHEMA_SUBENTRY_UUID_AT, subentry.getUuid() );
                                }
                            }
                        }
                    }
                }

            case TriggerExecution :
                nodeAP = directoryService.getTriggerExecutionAPCache().getParentWithElement( dn );

                if ( nodeAP != null )
                {
                    AdministrativePoint parentAP = nodeAP.getElement();
    
                    if ( parentAP != null )
                    {
                        // Update the entry seqNumber for TE
                        entry.put( ApacheSchemaConstants.ACCESS_CONTROL_SEQ_NUMBER_AT, Long.toString( parentAP.getSeqNumber() ) );
                        
                        // This entry has a TriggerExecution AP parent.
                        Set<Subentry> subentries = parentAP.getSubentries();
    
                        if ( subentries != null )
                        {
                            for ( Subentry subentry : subentries )
                            {
                                SubtreeSpecification ss = subentry.getSubtreeSpecification();
        
                                // Now, evaluate the entry wrt the subentry ss
                                // and inject a ref to the subentry if it evaluates to true
                                if ( evaluator.evaluate( ss, nodeAP.getDn(), dn, entry ) )
                                {
                                    // Point to the subentry UUID
                                    entry.add( ApacheSchemaConstants.TRIGGER_EXECUTION_SUBENTRIES_UUID_AT, subentry.getUuid() );
                                }
                            }
                        }
                    }
                }
        }
    }
    
    
    /**
     * Add a reference to the added subentry in each of the AP cache for which the 
     * subentry has a role.
     */
    private void addSubentry( DN apDn, Entry entry, Subentry subentry, long seqNumber ) throws LdapException
    {
        for ( AdministrativeRole role : getSubentryAdminRoles( entry ) )
        {
            switch ( role )
            {
                case AccessControlSpecificArea :
                     AdministrativePoint apAC = directoryService.getAccessControlAPCache().getElement( apDn );
                     apAC.addSubentry( subentry );
                     apAC.setSeqNumber( seqNumber );
                     break;
                     
                case CollectiveAttributeSpecificArea :
                    AdministrativePoint apCA = directoryService.getCollectiveAttributeAPCache().getElement( apDn );
                    apCA.addSubentry( subentry );
                    apCA.setSeqNumber( seqNumber );
                    break;
                    
                case SubSchemaSpecificArea :
                    AdministrativePoint apSS = directoryService.getSubschemaAPCache().getElement( apDn );
                    apSS.addSubentry( subentry );
                    apSS.setSeqNumber( seqNumber );
                    break;
                    
                case TriggerExecutionSpecificArea :
                    AdministrativePoint apTE = directoryService.getTriggerExecutionAPCache().getElement( apDn );
                    apTE.addSubentry( subentry );
                    apTE.setSeqNumber( seqNumber );
                    break;
            }
        }
    }
    
    
    /**
     * Remove the reference to the deleted subentry in each of the AP cache for which the 
     * subentry has a role.
     */
    private void deleteSubentry( DN apDn, Entry entry, Subentry subentry ) throws LdapException
    {
        for ( AdministrativeRole role : getSubentryAdminRoles( entry ) )
        {
            switch ( role )
            {
                case AccessControlSpecificArea :
                     directoryService.getAccessControlAPCache().getElement( apDn ).deleteSubentry( subentry );
                     break;
                     
                case CollectiveAttributeSpecificArea :
                    directoryService.getCollectiveAttributeAPCache().getElement( apDn ).deleteSubentry( subentry );
                    break;
                    
                case SubSchemaSpecificArea :
                    directoryService.getSubschemaAPCache().getElement( apDn ).deleteSubentry( subentry );
                    break;
                    
                case TriggerExecutionSpecificArea :
                    directoryService.getTriggerExecutionAPCache().getElement( apDn ).deleteSubentry( subentry );
                    break;
                    
            }
        }
    }


    //-------------------------------------------------------------------------------------------
    // Interceptor API methods
    //-------------------------------------------------------------------------------------------
    /**
     * Add a new entry into the DIT. We deal with the Administrative aspects.
     * We have to manage the three kind of added element :
     * <ul>
     * <li>APs</li>
     * <li>SubEntries</li>
     * <li>Entries</li>
     * </ul>
     * 
     * @param next The next {@link Interceptor} in the chain
     * @param addContext The {@link AddOperationContext} instance
     * @throws LdapException If we had some error while processing the Add operation
     */
    public void add( NextInterceptor next, AddOperationContext addContext ) throws LdapException
    {
        LOG.debug( "Entering into the Subtree Interceptor, addRequest : {}", addContext );
        
        DN dn = addContext.getDn();
        Entry entry = addContext.getEntry();

        boolean isAdmin = addContext.getSession().getAuthenticatedPrincipal().getName().equals(
            ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );

        // Check if we are adding an Administrative Point
        EntryAttribute adminPointAT = entry.get( ADMINISTRATIVE_ROLE_AT );

        // First, deal with an AP addition
        if ( adminPointAT != null )
        {
            // Only admin can add an AP
            if ( !isAdmin )
            {
                String message = "Cannot add the given AdministrativePoint, user is not an Admin";
                LOG.error( message );
                
                throw new LdapUnwillingToPerformException( message );
            }

            LOG.debug( "Addition of an administrative point at {} for the roles {}", dn, adminPointAT );

            try
            {
                // Protect the AP caches against concurrent access
                lockWrite();

                // This is an AP, do the initial check
                if ( !checkIsValidAP( entry ) )
                {
                    String message = "Cannot add the given AP, it's not a valid one :" + entry;
                    LOG.error( message );
                    throw new LdapUnwillingToPerformException( message );
                }
                
                // Add a negative seqNumber 
                entry.add( AP_SEQ_NUMBER_AT, Long.toString( -1L ) );
                
                // Ok, we are golden.
                next.add( addContext );
    
                String apUuid = entry.get( ENTRY_UUID_AT ).getString();
    
                // Now, update the AdminPoint cache
                createAdministrativePoints( adminPointAT, dn, apUuid, -1 );
            }
            finally
            {
                // Release the APCaches lock
                unlock();
            }
            
            LOG.debug( "Added an Administrative Point at {}", dn );
        }
        else if ( entry.contains( OBJECT_CLASS_AT, SchemaConstants.SUBENTRY_OC ) )
        {
            // It's a subentry
            if ( !isAdmin )
            {
                String message = "Cannot add the given Subentry, user is not an Admin";
                LOG.error( message );
                
                throw new LdapUnwillingToPerformException( message );
            }
            
            // Get the administrativePoint role : we must have one immediately
            // upper
            DN apDn = dn.getParent();
            
            try
            {
                // Protect the AP caches while checking the subentry
                lockRead();
    
                if ( !isValidSubentry( entry,  apDn ) )
                {
                    String message = "Cannot add the given Subentry, it does not have a parent AP";
                    LOG.error( message );
                    
                    throw new LdapUnwillingToPerformException( message );
                }
                
                /* ----------------------------------------------------------------
                 * Build the set of operational attributes to be injected into
                 * entries that are contained within the subtree represented by this
                 * new subentry.  In the process we make sure the proper roles are
                 * supported by the administrative point to allow the addition of
                 * this new subentry.
                 * ----------------------------------------------------------------
                 */
                Subentry subentry = new Subentry();
                subentry.setAdministrativeRoles( getSubentryAdminRoles( entry ) );
                //List<EntryAttribute> operationalAttributes = getSubentryOperationalAttributes( dn, subentry );
    
                /* ----------------------------------------------------------------
                 * Parse the subtreeSpecification of the subentry and add it to the
                 * SubtreeSpecification cache.  If the parse succeeds we continue
                 * to add the entry to the DIT.  Thereafter we search out entries
                 * to modify the subentry operational attributes of.
                 * ----------------------------------------------------------------
                 */
                setSubtreeSpecification( subentry, entry );
                subentryCache.addSubentry( dn, subentry );

                // Update the seqNumber and update the parent AP
                long seqNumber = updateSeqNumber( apDn );
    
                // Now inject the subentry into the backend
                next.add( addContext );
                
                // Get back the entryUUID and store it in the subentry
                String subentryUuid = addContext.getEntry().get( SchemaConstants.ENTRY_UUID_AT ).getString();
                subentry.setUuid( subentryUuid );

                // Inject the subentry into its parent APs cache
                addSubentry( apDn, entry, subentry, seqNumber );
            }
            finally
            {
                // Release the APCaches lock
                unlock();
            }
            
            LOG.debug( "Added a subentry at {}", dn );
        }
        else
        {
            // The added entry is a normal entry
            // We have to process the addition for each role
            processAddEntry( AdministrativeRoleEnum.AccessControl, entry );
            processAddEntry( AdministrativeRoleEnum.CollectiveAttribute, entry );
            processAddEntry( AdministrativeRoleEnum.TriggerExecution, entry );
            processAddEntry( AdministrativeRoleEnum.SubSchema, entry );

            // Propagate the addition down to the backend.
            next.add( addContext );
        }
    }


    /**
     * {@inheritDoc}
     */
    public void delete( NextInterceptor next, DeleteOperationContext deleteContext ) throws LdapException
    {
        DN dn = deleteContext.getDn();
        Entry entry = deleteContext.getEntry();

        // Check if we are deleting an Administrative Point
        EntryAttribute adminPointAT = entry.get( ADMINISTRATIVE_ROLE_AT );

        boolean isAdmin = deleteContext.getSession().getAuthenticatedPrincipal().getName().equals(
            ServerDNConstants.ADMIN_SYSTEM_DN_NORMALIZED );

        // First, deal with an AP deletion
        if ( adminPointAT != null )
        {
            if ( !isAdmin )
            {
                String message = "Cannot delete the given AdministrativePoint, user is not an Admin";
                LOG.error( message );
                
                throw new LdapUnwillingToPerformException( message );
            }
            
            // It's an AP : we can delete the entry, and if done successfully,
            // we can update the APCache for each role
            next.delete( deleteContext );
            
            // Now, update the AP cache
            deleteAdministrativePoints( adminPointAT, dn );
        }
        else if ( entry.contains( OBJECT_CLASS_AT, SchemaConstants.SUBENTRY_OC ) )
        {
            // It's a subentry. We ust be admin t remove it
            if ( !isAdmin )
            {
                String message = "Cannot delete the given Subentry, user is not an Admin";
                LOG.error( message );
                
                throw new LdapUnwillingToPerformException( message );
            }
            
            // Now delete the subentry itself
            next.delete( deleteContext );

            // Update the subentry cache
            Subentry removedSubentry = subentryCache.removeSubentry( dn );

            // Get the administrativePoint role : we must have one immediately
            // upper
            DN apDn = dn.getParent();

            // Update the parent AP seqNumber for the roles the subentry was handling
            AdministrativePoint adminPoint = getAdministrativePoint( apDn );
            adminPoint.deleteSubentry( removedSubentry );
            
            // And finally, update the parent AP SeqNumber
        }
        else
        {
            // This is a normal entry : propagate the deletion down to the backend
            next.delete( deleteContext );
        }
    }


    /**
     * {@inheritDoc}
     */
    public EntryFilteringCursor list( NextInterceptor nextInterceptor, ListOperationContext listContext )
        throws LdapException
    {
        EntryFilteringCursor cursor = nextInterceptor.list( listContext );

        if ( !isSubentryVisible( listContext ) )
        {
            cursor.addEntryFilter( new HideSubentriesFilter() );
        }

        return cursor;
    }


    /**
     * {@inheritDoc}
     */
    public void modify( NextInterceptor next, ModifyOperationContext modifyContext ) throws LdapException
    {
        DN dn = modifyContext.getDn();
        List<Modification> modifications = modifyContext.getModItems();

        Entry entry = modifyContext.getEntry();

        // We have three types of modifications :
        // 1) A modification applied on a normal entry
        // 2) A modification done on a subentry (the entry will have a 'subentry' ObjectClass)
        // 3) A modification on a normal entry on whch we add a 'subentry' ObjectClass
        // The third case is a transformation of a normal entry to a subentry. Not sure if it's
        // legal ...

        boolean isSubtreeSpecificationModification = false;
        Modification subtreeMod = null;

        // Find the subtreeSpecification
        for ( Modification mod : modifications )
        {
            if ( mod.getAttribute().getAttributeType().equals( SUBTREE_SPECIFICATION_AT ) )
            {
                isSubtreeSpecificationModification = true;
                subtreeMod = mod;
                break;
            }
        }

        boolean containsSubentryOC = entry.contains( OBJECT_CLASS_AT, SchemaConstants.SUBENTRY_OC );

        // Check if we have a modified subentry attribute in a Subentry entry
        if ( containsSubentryOC && isSubtreeSpecificationModification )
        {
            Subentry subentry = subentryCache.removeSubentry( dn );
            SubtreeSpecification ssOld = subentry.getSubtreeSpecification();
            SubtreeSpecification ssNew;

            try
            {
                ssNew = ssParser.parse( subtreeMod.getAttribute().getString() );
            }
            catch ( Exception e )
            {
                String msg = I18n.err( I18n.ERR_71 );
                LOG.error( msg, e );
                throw new LdapInvalidAttributeValueException( ResultCodeEnum.INVALID_ATTRIBUTE_SYNTAX, msg );
            }

            subentry.setSubtreeSpecification( ssNew );
            subentry.setAdministrativeRoles( getSubentryTypes( entry, modifications ) );
            subentryCache.addSubentry( dn, subentry );

            next.modify( modifyContext );

            // search for all entries selected by the old SS and remove references to subentry
            DN apName = dn.getParent();
            DN oldBaseDn = apName;
            oldBaseDn = oldBaseDn.addAll( ssOld.getBase() );

            ExprNode filter = new PresenceNode( OBJECT_CLASS_AT );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            SearchOperationContext searchOperationContext = new SearchOperationContext( modifyContext.getSession(),
                oldBaseDn, filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );

            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            try
            {
                while ( subentries.next() )
                {
                    Entry candidate = subentries.get();
                    DN candidateDn = candidate.getDn();

                    if ( evaluator.evaluate( ssOld, apName, candidateDn, candidate ) )
                    {
                        nexus.modify( new ModifyOperationContext( modifyContext.getSession(), candidateDn,
                            getOperationalModsForRemove( dn, candidate ) ) );
                    }
                }
            }
            catch ( Exception e )
            {
                throw new LdapOperationErrorException( e.getMessage() );
            }

            // search for all selected entries by the new SS and add references to subentry
            subentry = subentryCache.getSubentry( dn );
            List<EntryAttribute> operationalAttributes = getSubentryOperationalAttributes( dn, subentry );
            DN newBaseDn = apName;
            newBaseDn = newBaseDn.addAll( ssNew.getBase() );

            searchOperationContext = new SearchOperationContext( modifyContext.getSession(), newBaseDn, filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );

            subentries = nexus.search( searchOperationContext );

            try
            {
                while ( subentries.next() )
                {
                    Entry candidate = subentries.get();
                    DN candidateDn = candidate.getDn();

                    if ( evaluator.evaluate( ssNew, apName, candidateDn, candidate ) )
                    {
                        nexus.modify( new ModifyOperationContext( modifyContext.getSession(), candidateDn,
                            getOperationalModsForAdd( candidate, operationalAttributes ) ) );
                    }
                }
            }
            catch ( Exception e )
            {
                throw new LdapOperationErrorException( e.getMessage() );
            }
        }
        else
        {
            next.modify( modifyContext );

            if ( !containsSubentryOC )
            {
                Entry newEntry = modifyContext.getAlteredEntry();

                List<Modification> subentriesOpAttrMods = getModsOnEntryModification( dn, entry, newEntry );

                if ( subentriesOpAttrMods.size() > 0 )
                {
                    nexus.modify( new ModifyOperationContext( modifyContext.getSession(), dn, subentriesOpAttrMods ) );
                }
            }
        }
    }


    /**
     * The Move operation for a Subentry will deal with different cases :
     * 1) we move a normal entry
     * 2) we move a subentry
     * 3) we move an administrationPoint
     * <p>
     * <u>Case 1 :</u><br>
     * A normal entry (ie, not a subentry or an AP) may be part of some administrative areas.
     * We have to remove the references to the associated areas if the entry gets out of them.<br>
     * This entry can also be moved to some other administrative area, and it should then be
     * updated to point to the associated subentries.
     * <br><br>
     * There is one preliminary condition : If the entry has a descendant which is an
     * Administrative Point, then the move cannot be done.
     * <br><br>
     * <u>Case 2 :</u><br>
     * The subentry has to be moved under a new AP, otherwise this is an error. Once moved,
     * we have to update all the entries selected by the old subtreeSpecification, removing
     * the references to the subentry from all the selected entry, and update the entries
     * selected by the new subtreeSpecification by adding a reference to the subentry into them.
     * <br><br>
     * <u>Case 3 :</u><br>
     *
     *
     * @param next The next interceptor in the chain
     * @param moveContext The context containing all the needed informations to proceed
     * @throws LdapException If the move failed
     */
    public void move( NextInterceptor next, MoveOperationContext moveContext ) throws LdapException
    {
        DN oldDn = moveContext.getDn();
        DN newSuperiorDn = moveContext.getNewSuperior();

        Entry entry = moveContext.getOriginalEntry();

        if ( entry.contains( OBJECT_CLASS_AT, SchemaConstants.SUBENTRY_OC ) )
        {
            // This is a subentry. Moving a subentry means we have to:
            // o Check that there is a new AP where we move the subentry
            // o Remove the op Attr from all the entry selected by the subentry
            // o Add the op Attr in all the selected entry by the subentry

            // If we move it, we have to check that
            // the new parent is an AP
            //checkAdministrativeRole( moveContext, newSuperiorDn );

            Subentry subentry = subentryCache.removeSubentry( oldDn );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();
            DN apName = oldDn.getParent();
            DN baseDn = apName;
            baseDn = baseDn.addAll( ss.getBase() );
            DN newName = newSuperiorDn;
            newName = newName.add( oldDn.getRdn() );
            newName.normalize( schemaManager );

            subentryCache.addSubentry( newName, subentry );

            next.move( moveContext );

            subentry = subentryCache.getSubentry( newName );

            ExprNode filter = new PresenceNode( OBJECT_CLASS_AT );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            SearchOperationContext searchOperationContext = new SearchOperationContext( moveContext.getSession(), baseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );

            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            try
            {
                // Modify all the entries under this subentry
                while ( subentries.next() )
                {
                    Entry candidate = subentries.get();
                    DN dn = candidate.getDn();
                    dn.normalize( schemaManager );

                    if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                    {
                        nexus.modify( new ModifyOperationContext( moveContext.getSession(), dn, getOperationalModsForReplace(
                            oldDn, newName, subentry, candidate ) ) );
                    }
                }

                subentries.close();
            }
            catch ( Exception e )
            {
                throw new LdapOperationException( e.getMessage() );
            }
        }
        else
        {
            // A normal entry. It may be part of a SubtreeSpecifciation. In this
            // case, we have to update the opAttrs (removing old ones and adding the
            // new ones)

            // First, an moved entry which has an AP in one of its descendant
            // can't be moved.
            if ( hasAdministrativeDescendant( moveContext, oldDn ) )
            {
                String msg = I18n.err( I18n.ERR_308 );
                LOG.warn( msg );
                throw new LdapSchemaViolationException( ResultCodeEnum.NOT_ALLOWED_ON_RDN, msg );
            }

            // Move the entry
            next.move( moveContext );

            // calculate the new DN now for use below to modify subentry operational
            // attributes contained within this regular entry with name changes
            DN newDn = moveContext.getNewDn();
            List<Modification> mods = getModsOnEntryRdnChange( oldDn, newDn, entry );

            // Update the entry operational attributes
            if ( mods.size() > 0 )
            {
                nexus.modify( new ModifyOperationContext( moveContext.getSession(), newDn, mods ) );
            }
        }
    }


    public void moveAndRename( NextInterceptor next, MoveAndRenameOperationContext moveAndRenameContext ) throws LdapException
    {
        DN oldDn = moveAndRenameContext.getDn();
        DN newSuperiorDn = moveAndRenameContext.getNewSuperiorDn();

        Entry entry = moveAndRenameContext.getOriginalEntry();

        if ( entry.contains( OBJECT_CLASS_AT, SchemaConstants.SUBENTRY_OC ) )
        {
            Subentry subentry = subentryCache.removeSubentry( oldDn );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();
            DN apName = oldDn.getParent();
            DN baseDn = apName;
            baseDn = baseDn.addAll( ss.getBase() );
            DN newName = newSuperiorDn.getParent();

            newName = newName.add( moveAndRenameContext.getNewRdn() );
            newName.normalize( schemaManager );

            subentryCache.addSubentry( newName, subentry );

            next.moveAndRename( moveAndRenameContext );

            subentry = subentryCache.getSubentry( newName );

            ExprNode filter = new PresenceNode( OBJECT_CLASS_AT );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            SearchOperationContext searchOperationContext = new SearchOperationContext( moveAndRenameContext.getSession(), baseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );

            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            try
            {
                while ( subentries.next() )
                {
                    Entry candidate = subentries.get();
                    DN dn = candidate.getDn();
                    dn.normalize( schemaManager );

                    if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                    {
                        nexus.modify( new ModifyOperationContext( moveAndRenameContext.getSession(), dn, getOperationalModsForReplace(
                            oldDn, newName, subentry, candidate ) ) );
                    }
                }

                subentries.close();
            }
            catch ( Exception e )
            {
                throw new LdapOperationException( e.getMessage() );
            }
        }
        else
        {
            if ( hasAdministrativeDescendant( moveAndRenameContext, oldDn ) )
            {
                String msg = I18n.err( I18n.ERR_308 );
                LOG.warn( msg );
                throw new LdapSchemaViolationException( ResultCodeEnum.NOT_ALLOWED_ON_RDN, msg );
            }

            next.moveAndRename( moveAndRenameContext );

            // calculate the new DN now for use below to modify subentry operational
            // attributes contained within this regular entry with name changes
            DN newDn = moveAndRenameContext.getNewDn();
            List<Modification> mods = getModsOnEntryRdnChange( oldDn, newDn, entry );

            if ( mods.size() > 0 )
            {
                nexus.modify( new ModifyOperationContext( moveAndRenameContext.getSession(), newDn, mods ) );
            }
        }
    }


    public void rename( NextInterceptor next, RenameOperationContext renameContext ) throws LdapException
    {
        DN oldDn = renameContext.getDn();

        Entry entry = renameContext.getEntry().getClonedEntry();

        if ( entry.contains( OBJECT_CLASS_AT, SchemaConstants.SUBENTRY_OC ) )
        {
            // @Todo To be reviewed !!!
            Subentry subentry = subentryCache.removeSubentry( oldDn );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();
            DN apName = oldDn.getParent();
            DN baseDn = apName;
            baseDn = baseDn.addAll( ss.getBase() );
            DN newName = oldDn.getParent();

            newName = newName.add( renameContext.getNewRdn() );
            newName.normalize( schemaManager );

            subentryCache.addSubentry( newName, subentry );
            next.rename( renameContext );

            subentry = subentryCache.getSubentry( newName );
            ExprNode filter = new PresenceNode( OBJECT_CLASS_AT );
            SearchControls controls = new SearchControls();
            controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
            controls.setReturningAttributes( new String[]
                { SchemaConstants.ALL_OPERATIONAL_ATTRIBUTES, SchemaConstants.ALL_USER_ATTRIBUTES } );

            SearchOperationContext searchOperationContext = new SearchOperationContext( renameContext.getSession(), baseDn,
                filter, controls );
            searchOperationContext.setAliasDerefMode( AliasDerefMode.NEVER_DEREF_ALIASES );

            EntryFilteringCursor subentries = nexus.search( searchOperationContext );

            try
            {
                while ( subentries.next() )
                {
                    Entry candidate = subentries.get();
                    DN dn = candidate.getDn();
                    dn.normalize( schemaManager );

                    if ( evaluator.evaluate( ss, apName, dn, candidate ) )
                    {
                        nexus.modify( new ModifyOperationContext( renameContext.getSession(), dn, getOperationalModsForReplace(
                            oldDn, newName, subentry, candidate ) ) );
                    }
                }

                subentries.close();
            }
            catch ( Exception e )
            {
                throw new LdapOperationException( e.getMessage() );
            }
        }
        else
        {
            if ( hasAdministrativeDescendant( renameContext, oldDn ) )
            {
                String msg = I18n.err( I18n.ERR_308 );
                LOG.warn( msg );
                throw new LdapSchemaViolationException( ResultCodeEnum.NOT_ALLOWED_ON_RDN, msg );
            }

            next.rename( renameContext );

            // calculate the new DN now for use below to modify subentry operational
            // attributes contained within this regular entry with name changes
            DN newName = renameContext.getNewDn();

            List<Modification> mods = getModsOnEntryRdnChange( oldDn, newName, entry );

            if ( mods.size() > 0 )
            {
                nexus.modify( new ModifyOperationContext( renameContext.getSession(), newName, mods ) );
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    public EntryFilteringCursor search( NextInterceptor nextInterceptor, SearchOperationContext searchContext )
        throws LdapException
    {
        EntryFilteringCursor cursor = nextInterceptor.search( searchContext );

        // object scope searches by default return subentries
        if ( searchContext.getScope() == SearchScope.OBJECT )
        {
            return cursor;
        }

        // for subtree and one level scope we filter
        if ( !isSubentryVisible( searchContext ) )
        {
            cursor.addEntryFilter( new HideSubentriesFilter() );
        }
        else
        {
            cursor.addEntryFilter( new HideEntriesFilter() );
        }

        return cursor;
    }


    //-------------------------------------------------------------------------------------------
    // Shared method
    //-------------------------------------------------------------------------------------------
    /**
     * Evaluates the set of subentry subtrees upon an entry and returns the
     * operational subentry attributes that will be added to the entry if
     * added at the dn specified.
     *
     * @param dn the normalized distinguished name of the entry
     * @param entryAttrs the entry attributes are generated for
     * @return the set of subentry op attrs for an entry
     * @throws Exception if there are problems accessing entry information
     */
    public Entry getSubentryAttributes( DN dn, Entry entryAttrs ) throws LdapException
    {
        Entry subentryAttrs = new DefaultEntry( schemaManager, dn );

        for ( DN subentryDn : subentryCache )
        {
            DN apDn = subentryDn.getParent();
            Subentry subentry = subentryCache.getSubentry( subentryDn );
            SubtreeSpecification ss = subentry.getSubtreeSpecification();

            if ( evaluator.evaluate( ss, apDn, dn, entryAttrs ) )
            {
                EntryAttribute operational;

                if ( subentry.isAccessControlAdminRole() )
                {
                    operational = subentryAttrs.get( ACCESS_CONTROL_SUBENTRIES_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultEntryAttribute( ACCESS_CONTROL_SUBENTRIES_AT );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.getNormName() );
                }

                if ( subentry.isSchemaAdminRole() )
                {
                    operational = subentryAttrs.get( SUBSCHEMA_SUBENTRY_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultEntryAttribute( SUBSCHEMA_SUBENTRY_AT );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.getNormName() );
                }

                if ( subentry.isCollectiveAdminRole() )
                {
                    operational = subentryAttrs.get( COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultEntryAttribute( COLLECTIVE_ATTRIBUTE_SUBENTRIES_AT );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.getNormName() );
                }

                if ( subentry.isTriggersAdminRole() )
                {
                    operational = subentryAttrs.get( TRIGGER_EXECUTION_SUBENTRIES_AT );

                    if ( operational == null )
                    {
                        operational = new DefaultEntryAttribute( TRIGGER_EXECUTION_SUBENTRIES_AT );
                        subentryAttrs.put( operational );
                    }

                    operational.add( subentryDn.getNormName() );
                }
            }
        }

        return subentryAttrs;
    }
}
