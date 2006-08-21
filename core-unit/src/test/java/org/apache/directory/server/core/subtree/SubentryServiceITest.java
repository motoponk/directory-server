/*
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.directory.server.core.subtree;


import org.apache.directory.server.core.subtree.SubentryService;
import org.apache.directory.server.core.unit.AbstractAdminTestCase;
import org.apache.directory.shared.ldap.exception.LdapNoSuchAttributeException;
import org.apache.directory.shared.ldap.message.LockableAttributeImpl;
import org.apache.directory.shared.ldap.message.LockableAttributesImpl;
import org.apache.directory.shared.ldap.message.SubentriesControl;

import javax.naming.NamingException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import javax.naming.ldap.Control;

import java.util.Map;
import java.util.HashMap;


/**
 * Testcases for the SubentryService.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class SubentryServiceITest extends AbstractAdminTestCase
{
    public Attributes getTestEntry( String cn )
    {
        Attributes subentry = new LockableAttributesImpl();
        Attribute objectClass = new LockableAttributeImpl( "objectClass" );
        objectClass.add( "top" );
        objectClass.add( "person" );
        subentry.put( objectClass );
        subentry.put( "cn", cn );
        subentry.put( "sn", "testentry" );
        return subentry;
    }


    public Attributes getTestSubentry()
    {
        Attributes subentry = new LockableAttributesImpl();
        Attribute objectClass = new LockableAttributeImpl( "objectClass" );
        objectClass.add( "top" );
        objectClass.add( "subentry" );
        subentry.put( objectClass );
        subentry.put( "subtreeSpecification", "{ base \"ou=configuration\" }" );
        subentry.put( "cn", "testsubentry" );
        return subentry;
    }


    public Attributes getTestSubentryWithExclusion()
    {
        Attributes subentry = new LockableAttributesImpl();
        Attribute objectClass = new LockableAttributeImpl( "objectClass" );
        objectClass.add( "top" );
        objectClass.add( "subentry" );
        subentry.put( objectClass );
        String spec = "{ base \"ou=configuration\", specificExclusions { chopBefore:\"cn=unmarked\" } }";
        subentry.put( "subtreeSpecification", spec );
        subentry.put( "cn", "testsubentry" );
        return subentry;
    }


    public void addAdministrativeRole( String role ) throws NamingException
    {
        Attribute attribute = new LockableAttributeImpl( "administrativeRole" );
        attribute.add( role );
        ModificationItem item = new ModificationItem( DirContext.ADD_ATTRIBUTE, attribute );
        super.sysRoot.modifyAttributes( "", new ModificationItem[]
            { item } );
    }


    public Map getAllEntries() throws NamingException
    {
        Map resultMap = new HashMap();
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        controls.setReturningAttributes( new String[]
            { "+", "*" } );
        NamingEnumeration results = super.sysRoot.search( "", "(objectClass=*)", controls );
        while ( results.hasMore() )
        {
            SearchResult result = ( SearchResult ) results.next();
            resultMap.put( result.getName(), result.getAttributes() );
        }
        return resultMap;
    }


    public void testEntryAdd() throws NamingException
    {
        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentry() );
        super.sysRoot.createSubcontext( "cn=unmarked", getTestEntry( "unmarked" ) );
        super.sysRoot.createSubcontext( "cn=marked,ou=configuration", getTestEntry( "marked" ) );
        Map results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        Attributes marked = ( Attributes ) results.get( "cn=marked,ou=configuration,ou=system" );
        Attribute autonomousSubentry = marked.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=marked,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        Attributes unmarked = ( Attributes ) results.get( "cn=unmarked,ou=system" );
        assertNull( "cn=unmarked,ou=system should not be marked", unmarked
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );
    }


    public void testSubentryAdd() throws NamingException
    {
        try
        {
            super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentry() );
            fail( "should never get here: cannot create subentry under regular entries" );
        }
        catch ( LdapNoSuchAttributeException e )
        {
        }

        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentry() );
        Map results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        Attributes configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        Attribute autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=interceptors,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=partitions,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=services,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        Attributes system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes groups = ( Attributes ) results.get( "ou=groups,ou=system" );
        assertNull( "ou=groups,ou=system should not be marked", groups.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

    }


    public void testSubentryModify() throws NamingException
    {
        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentry() );
        Map results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        Attributes configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        Attribute autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=interceptors,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=partitions,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=services,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        Attributes system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes groups = ( Attributes ) results.get( "ou=groups,ou=system" );
        assertNull( "ou=groups,ou=system should not be marked", groups.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        // --------------------------------------------------------------------
        // Now modify the subentry by introducing an exclusion
        // --------------------------------------------------------------------

        Attribute subtreeSpecification = new LockableAttributeImpl( "subtreeSpecification" );
        subtreeSpecification.add( "{ base \"ou=configuration\", specificExclusions { chopBefore:\"ou=services\" } }" );
        ModificationItem item = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, subtreeSpecification );
        super.sysRoot.modifyAttributes( "cn=testsubentry", new ModificationItem[]
            { item } );
        results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=interceptors,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=partitions,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        groups = ( Attributes ) results.get( "ou=groups,ou=system" );
        assertNull( "ou=groups,ou=system should not be marked", groups.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        if ( autonomousSubentry != null )
        {
            assertEquals( "ou=services,ou=configuration,ou=system should not be marked", 0, autonomousSubentry.size() );
        }
    }


    public void testSubentryModify2() throws NamingException
    {
        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentry() );
        Map results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        Attributes configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        Attribute autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=interceptors,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=partitions,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=services,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        Attributes system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes groups = ( Attributes ) results.get( "ou=groups,ou=system" );
        assertNull( "ou=groups,ou=system should not be marked", groups.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        // --------------------------------------------------------------------
        // Now modify the subentry by introducing an exclusion
        // --------------------------------------------------------------------

        Attributes changes = new LockableAttributesImpl();
        changes.put( "subtreeSpecification",
            "{ base \"ou=configuration\", specificExclusions { chopBefore:\"ou=services\" } }" );
        super.sysRoot.modifyAttributes( "cn=testsubentry", DirContext.REPLACE_ATTRIBUTE, changes );
        results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=interceptors,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=partitions,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        groups = ( Attributes ) results.get( "ou=groups,ou=system" );
        assertNull( "ou=groups,ou=system should not be marked", groups.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        if ( autonomousSubentry != null )
        {
            assertEquals( "ou=services,ou=configuration,ou=system should not be marked", 0, autonomousSubentry.size() );
        }
    }


    public void testSubentryDelete() throws NamingException
    {
        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentry() );
        super.sysRoot.destroySubcontext( "cn=testsubentry" );

        Map results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        Attributes configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        Attribute autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        if ( autonomousSubentry != null )
        {
            assertEquals( "ou=configuration,ou=system should not be marked", 0, autonomousSubentry.size() );
        }

        Attributes interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        if ( autonomousSubentry != null )
        {
            assertEquals( "ou=interceptors,ou=configuration,ou=system should not be marked", 0, autonomousSubentry
                .size() );
        }

        Attributes partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        if ( autonomousSubentry != null )
        {
            assertEquals( "ou=partitions,ou=configuration,ou=system should not be marked", 0, autonomousSubentry.size() );
        }

        Attributes services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        if ( autonomousSubentry != null )
        {
            assertEquals( "ou=services,ou=configuration,ou=system should not be marked", 0, autonomousSubentry.size() );
        }

        Attributes system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

    }


    public void testSubentryModifyRdn() throws NamingException
    {
        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentry() );
        super.sysRoot.rename( "cn=testsubentry", "cn=newname" );
        Map results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        Attributes configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        Attribute autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=newname,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=interceptors,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=newname,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=partitions,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=newname,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=services,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=newname,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        Attributes system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes groups = ( Attributes ) results.get( "ou=groups,ou=system" );
        assertNull( "ou=groups,ou=system should not be marked", groups.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

    }


    public void testEntryModifyRdn() throws NamingException
    {
        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentryWithExclusion() );
        super.sysRoot.createSubcontext( "cn=unmarked,ou=configuration", getTestEntry( "unmarked" ) );
        super.sysRoot.createSubcontext( "cn=marked,ou=configuration", getTestEntry( "marked" ) );
        Map results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        Attributes configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        Attribute autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=interceptors,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=partitions,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=services,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes marked = ( Attributes ) results.get( "cn=marked,ou=configuration,ou=system" );
        autonomousSubentry = marked.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "cn=marked,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        Attributes system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes groups = ( Attributes ) results.get( "ou=groups,ou=system" );
        assertNull( "ou=groups,ou=system should not be marked", groups.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes unmarked = ( Attributes ) results.get( "cn=unmarked,ou=configuration,ou=system" );
        assertNull( "cn=unmarked,ou=configuration,ou=system should not be marked", unmarked
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        // --------------------------------------------------------------------
        // Now destry one of the marked/unmarked and rename to deleted entry
        // --------------------------------------------------------------------

        super.sysRoot.destroySubcontext( "cn=unmarked,ou=configuration" );
        super.sysRoot.rename( "cn=marked,ou=configuration", "cn=unmarked,ou=configuration" );
        results = getAllEntries();

        unmarked = ( Attributes ) results.get( "cn=unmarked,ou=configuration,ou=system" );
        assertNull( "cn=unmarked,ou=configuration,ou=system should not be marked", unmarked
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );
        assertNull( results.get( "cn=marked,ou=configuration,ou=system" ) );

        // --------------------------------------------------------------------
        // Now rename unmarked to marked and see that subentry op attr is there
        // --------------------------------------------------------------------

        super.sysRoot.rename( "cn=unmarked,ou=configuration", "cn=marked,ou=configuration" );
        results = getAllEntries();
        assertNull( results.get( "cn=unmarked,ou=configuration,ou=system" ) );
        marked = ( Attributes ) results.get( "cn=marked,ou=configuration,ou=system" );
        assertNotNull( marked );
        autonomousSubentry = marked.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "cn=marked,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );
    }


    public void testEntryMoveWithRdnChange() throws NamingException
    {
        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentryWithExclusion() );
        super.sysRoot.createSubcontext( "cn=unmarked", getTestEntry( "unmarked" ) );
        super.sysRoot.createSubcontext( "cn=marked,ou=configuration", getTestEntry( "marked" ) );
        Map results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        Attributes configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        Attribute autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=interceptors,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=partitions,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=services,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes marked = ( Attributes ) results.get( "cn=marked,ou=configuration,ou=system" );
        autonomousSubentry = marked.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "cn=marked,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        Attributes system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes groups = ( Attributes ) results.get( "ou=groups,ou=system" );
        assertNull( "ou=groups,ou=system should not be marked", groups.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes unmarked = ( Attributes ) results.get( "cn=unmarked,ou=system" );
        assertNull( "cn=unmarked,ou=system should not be marked", unmarked
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        // --------------------------------------------------------------------
        // Now destry one of the marked/unmarked and rename to deleted entry
        // --------------------------------------------------------------------

        super.sysRoot.destroySubcontext( "cn=unmarked" );
        super.sysRoot.rename( "cn=marked,ou=configuration", "cn=unmarked" );
        results = getAllEntries();

        unmarked = ( Attributes ) results.get( "cn=unmarked,ou=system" );
        assertNull( "cn=unmarked,ou=system should not be marked", unmarked
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );
        assertNull( results.get( "cn=marked,ou=configuration,ou=system" ) );

        // --------------------------------------------------------------------
        // Now rename unmarked to marked and see that subentry op attr is there
        // --------------------------------------------------------------------

        super.sysRoot.rename( "cn=unmarked", "cn=marked,ou=configuration" );
        results = getAllEntries();
        assertNull( results.get( "cn=unmarked,ou=system" ) );
        marked = ( Attributes ) results.get( "cn=marked,ou=configuration,ou=system" );
        assertNotNull( marked );
        autonomousSubentry = marked.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "cn=marked,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );
    }


    public void testEntryMove() throws NamingException
    {
        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentryWithExclusion() );
        super.sysRoot.createSubcontext( "cn=unmarked", getTestEntry( "unmarked" ) );
        super.sysRoot.createSubcontext( "cn=marked,ou=configuration", getTestEntry( "marked" ) );
        Map results = getAllEntries();

        // --------------------------------------------------------------------
        // Make sure entries selected by the subentry do have the mark
        // --------------------------------------------------------------------

        Attributes configuration = ( Attributes ) results.get( "ou=configuration,ou=system" );
        Attribute autonomousSubentry = configuration.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes interceptors = ( Attributes ) results.get( "ou=interceptors,ou=configuration,ou=system" );
        autonomousSubentry = interceptors.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=interceptors,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes partitions = ( Attributes ) results.get( "ou=partitions,ou=configuration,ou=system" );
        autonomousSubentry = partitions.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=partitions,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes services = ( Attributes ) results.get( "ou=services,ou=configuration,ou=system" );
        autonomousSubentry = services.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "ou=services,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        Attributes marked = ( Attributes ) results.get( "cn=marked,ou=configuration,ou=system" );
        autonomousSubentry = marked.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "cn=marked,ou=configuration,ou=system should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );

        // --------------------------------------------------------------------
        // Make sure entries not selected by subentry do not have the mark
        // --------------------------------------------------------------------

        Attributes system = ( Attributes ) results.get( "ou=system" );
        assertNull( "ou=system should not be marked", system.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes users = ( Attributes ) results.get( "ou=users,ou=system" );
        assertNull( "ou=users,ou=system should not be marked", users.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes groups = ( Attributes ) results.get( "ou=groups,ou=system" );
        assertNull( "ou=groups,ou=system should not be marked", groups.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes admin = ( Attributes ) results.get( "uid=admin,ou=system" );
        assertNull( "uid=admin,ou=system should not be marked", admin.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes sysPrefRoot = ( Attributes ) results.get( "prefNodeName=sysPrefRoot,ou=system" );
        assertNull( "prefNode=sysPrefRoot,ou=system should not be marked", sysPrefRoot
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        Attributes unmarked = ( Attributes ) results.get( "cn=unmarked,ou=system" );
        assertNull( "cn=unmarked,ou=system should not be marked", unmarked
            .get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY ) );

        // --------------------------------------------------------------------
        // Now destry one of the marked/unmarked and rename to deleted entry
        // --------------------------------------------------------------------

        super.sysRoot.destroySubcontext( "cn=unmarked" );
        super.sysRoot.rename( "cn=marked,ou=configuration", "cn=marked,ou=services,ou=configuration" );
        results = getAllEntries();

        unmarked = ( Attributes ) results.get( "cn=unmarked,ou=system" );
        assertNull( "cn=unmarked,ou=system should not be marked", unmarked );
        assertNull( results.get( "cn=marked,ou=configuration,ou=system" ) );

        marked = ( Attributes ) results.get( "cn=marked,ou=services,ou=configuration,ou=system" );
        assertNotNull( marked );
        autonomousSubentry = marked.get( SubentryService.AUTONOUMOUS_AREA_SUBENTRY );
        assertNotNull( "cn=marked,ou=services,ou=configuration should be marked", autonomousSubentry );
        assertEquals( "2.5.4.3=testsubentry,2.5.4.11=system", autonomousSubentry.get() );
        assertEquals( 1, autonomousSubentry.size() );
    }


    public void testSubentriesControl() throws Exception
    {
        addAdministrativeRole( "autonomousArea" );
        super.sysRoot.createSubcontext( "cn=testsubentry", getTestSubentryWithExclusion() );
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope( SearchControls.SUBTREE_SCOPE );

        // perform the search without the control
        Map entries = new HashMap();
        NamingEnumeration list = super.sysRoot.search( "", "(objectClass=*)", searchControls );
        while ( list.hasMore() )
        {
            SearchResult result = ( SearchResult ) list.next();
            entries.put( result.getName(), result );
        }
        assertTrue( entries.size() > 1 );
        assertNull( entries.get( "cn=testsubentry,ou=system" ) );

        // now add the control with visibility set to true where all entries 
        // except subentries disappear
        SubentriesControl ctl = new SubentriesControl();
        ctl.setVisibility( true );
        super.sysRoot.setRequestControls( new Control[]
            { ctl } );
        list = super.sysRoot.search( "", "(objectClass=*)", searchControls );
        SearchResult result = ( SearchResult ) list.next();
        assertFalse( list.hasMore() );
        assertEquals( "cn=testsubentry,ou=system", result.getName() );
    }
}
