/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.directory.server.core.changelog;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.directory.server.core.DirectoryService;
import org.apache.directory.server.core.authn.LdapPrincipal;
import org.apache.directory.server.core.cursor.Cursor;
import org.apache.directory.server.core.cursor.ListCursor;
import org.apache.directory.shared.ldap.ldif.LdifEntry;
import org.apache.directory.shared.ldap.util.DateUtils;


/**
 * A change log store that keeps it's information in memory.
 *
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 * TODO remove the NamingException
 */
public class MemoryChangeLogStore implements TaggableChangeLogStore
{
    private static final String REV_FILE = "revision";
    private static final String TAG_FILE = "tags";
    private static final String CHANGELOG_FILE = "changelog.dat";

    private long currentRevision;
    private Tag latest;
    private final Map<Long,Tag> tags = new HashMap<Long,Tag>( 100 );
    private final List<ChangeLogEvent> events = new ArrayList<ChangeLogEvent>();
    private File workingDirectory;


    public Tag tag( long revision ) throws Exception
    {
        if ( tags.containsKey( revision ) )
        {
            return tags.get( revision );
        }

        latest = new Tag( revision, null );
        tags.put( revision, latest );
        return latest;
    }


    public Tag tag() throws Exception
    {
        if ( latest != null && latest.getRevision() == currentRevision )
        {
            return latest;
        }

        latest = new Tag( currentRevision, null );
        tags.put( currentRevision, latest );
        return latest;
    }


    public Tag tag( String description ) throws Exception
    {
        if ( latest != null && latest.getRevision() == currentRevision )
        {
            return latest;
        }

        latest = new Tag( currentRevision, description );
        tags.put( currentRevision, latest );
        return latest;
    }


    public void init( DirectoryService service ) throws Exception
    {
        workingDirectory = service.getWorkingDirectory();
        loadRevision();
        loadTags();
        loadChangeLog();
    }


    private void loadRevision() throws Exception
    {
        File revFile = new File( workingDirectory, REV_FILE );
        if ( revFile.exists() )
        {
            BufferedReader reader = null;
            try
            {
                reader = new BufferedReader( new FileReader( revFile ) );
                String line = reader.readLine();
                currentRevision = Long.valueOf( line );
            }
            catch ( IOException e )
            {
                throw e;
            }
            finally
            {
                if ( reader != null )
                {
                    //noinspection EmptyCatchBlock
                    try
                    {
                        reader.close();
                    }
                    catch ( IOException e )
                    {
                    }
                }
            }
        }
    }


    private void saveRevision() throws Exception
    {
        File revFile = new File( workingDirectory, REV_FILE );
        if ( revFile.exists() )
        {
            revFile.delete();
        }

        PrintWriter out = null;
        try
        {
            out = new PrintWriter( new FileWriter( revFile ) );
            out.println( currentRevision );
            out.flush();
        }
        catch ( IOException e )
        {
            throw e;
        }
        finally
        {
            if ( out != null )
            {
                out.close();
            }
        }
    }


    private void saveTags() throws Exception
    {
        File tagFile = new File( workingDirectory, TAG_FILE );
        if ( tagFile.exists() )
        {
            tagFile.delete();
        }

        FileOutputStream out = null;
        try
        {
            out = new FileOutputStream( tagFile );

            Properties props = new Properties();
            for ( Tag tag : tags.values() )
            {
                String key = String.valueOf( tag.getRevision() );
                if ( tag.getDescription() == null )
                {
                    props.setProperty( key, "null" );
                }
                else
                {
                    props.setProperty( key, tag.getDescription() );
                }
            }

            props.store( out, null );
            out.flush();
        }
        catch ( IOException e )
        {
            throw e;
        }
        finally
        {
            if ( out != null )
            {
                //noinspection EmptyCatchBlock
                try
                {
                    out.close();
                }
                catch ( IOException e )
                {
                }
            }
        }
    }


    private void loadTags() throws Exception
    {
        File revFile = new File( workingDirectory, REV_FILE );
        if ( revFile.exists() )
        {
            Properties props = new Properties();
            FileInputStream in = null;
            try
            {
                in = new FileInputStream( revFile );
                props.load( in );
                ArrayList<Long> revList = new ArrayList<Long>();
                for ( Object key : props.keySet() )
                {
                    revList.add( Long.valueOf( ( String ) key ) );
                }

                Collections.sort( revList );
                Tag tag = null;

                // @todo need some serious syncrhoization here on tags
                tags.clear();
                for ( Long lkey : revList )
                {
                    String rev = String.valueOf( lkey );
                    String desc = props.getProperty( rev );

                    if ( desc != null && desc.equals( "null" ) )
                    {
                        tag = new Tag( lkey, null );
                    }
                    else
                    {
                        tag = new Tag( lkey, desc );
                    }

                    tags.put( lkey, tag );
                }

                latest = tag;
            }
            catch ( IOException e )
            {
                throw e;
            }
            finally
            {
                if ( in != null )
                {
                    //noinspection EmptyCatchBlock
                    try
                    {
                        in.close();
                    }
                    catch ( IOException e )
                    {
                    }
                }
            }
        }
    }


    private void loadChangeLog() throws Exception
    {
        File file = new File( workingDirectory, CHANGELOG_FILE );
        if ( file.exists() )
        {
            ObjectInputStream in = null;

            try
            {
                in = new ObjectInputStream( new FileInputStream( file ) );
                ArrayList<ChangeLogEvent> changeLogEvents = new ArrayList<ChangeLogEvent>();

                while ( true )
                {
                    try
                    {
                        ChangeLogEvent event = ( ChangeLogEvent ) in.readObject();
                        changeLogEvents.add( event );
                    }
                    catch ( EOFException eofe )
                    {
                        break;
                    }
                }

                // @todo man o man we need some synchronization later after getting this to work
                this.events.clear();
                this.events.addAll( changeLogEvents );
            }
            catch ( Exception e )
            {
                throw e;
            }
            finally
            {
                if ( in != null )
                {
                    //noinspection EmptyCatchBlock
                    try
                    {
                        in.close();
                    }
                    catch ( IOException e )
                    {
                    }
                }
            }
        }
    }


    private void saveChangeLog() throws Exception
    {
        File file = new File( workingDirectory, CHANGELOG_FILE );
        if ( file.exists() )
        {
            file.delete();
        }

        try
        {
            file.createNewFile();
        }
        catch ( IOException e )
        {
            throw e;
        }

        ObjectOutputStream out = null;

        try
        {
            out = new ObjectOutputStream( new FileOutputStream( file ) );

            for ( ChangeLogEvent event : events )
            {
                out.writeObject( event );
            }

            out.flush();
        }
        catch ( Exception e )
        {
            throw e;
        }
        finally
        {
            if ( out != null )
            {
                //noinspection EmptyCatchBlock
                try
                {
                    out.close();
                }
                catch ( IOException e )
                {
                }
            }
        }
    }


    public void sync() throws Exception
    {
        saveRevision();
        saveTags();
        saveChangeLog();
    }


    public void destroy() throws Exception
    {
        saveRevision();
        saveTags();
        saveChangeLog();
    }


    public long getCurrentRevision()
    {
        return currentRevision;
    }


    public ChangeLogEvent log( LdapPrincipal principal, LdifEntry forward, LdifEntry reverse ) throws Exception
    {
        currentRevision++;
        ChangeLogEvent event = new ChangeLogEvent( currentRevision, DateUtils.getGeneralizedTime(), 
                principal, forward, reverse );
        events.add( event );
        return event;
    }


    public ChangeLogEvent lookup( long revision ) throws Exception
    {
        if ( revision < 0 )
        {
            throw new IllegalArgumentException( "revision must be greater than or equal to 0" );
        }

        if ( revision > getCurrentRevision() )
        {
            throw new IllegalArgumentException( "The revision must not be greater than the current revision" );
        }

        return events.get( ( int ) revision );
    }


    public Cursor<ChangeLogEvent> find() throws Exception
    {
        return new ListCursor<ChangeLogEvent>( events );
    }


    public Cursor<ChangeLogEvent> findBefore( long revision ) throws Exception
    {
        return new ListCursor<ChangeLogEvent>( events, ( int ) revision );
    }


    public Cursor<ChangeLogEvent> findAfter( long revision ) throws Exception
    {
        return new ListCursor<ChangeLogEvent>( ( int ) revision, events );
    }


    public Cursor<ChangeLogEvent> find( long startRevision, long endRevision ) throws Exception
    {
        return new ListCursor<ChangeLogEvent>( ( int ) startRevision, events, ( int ) ( endRevision + 1 ) );
    }


    public Tag getLatest() throws Exception
    {
        return latest;
    }


    /**
     * @see TaggableChangeLogStore#removeTag(long)
     */
    public Tag removeTag( long revision ) throws Exception
    {
        return tags.remove( revision );
    }


    /**
     * @see TaggableChangeLogStore#tag(long, String)
     */
    public Tag tag( long revision, String descrition ) throws Exception
    {
        if ( tags.containsKey( revision ) )
        {
            return tags.get( revision );
        }

        latest = new Tag( revision, descrition );
        tags.put( revision, latest );
        return latest;
    }
    
}
