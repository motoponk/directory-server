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
package org.apache.directory.server.xdbm;

import org.apache.directory.shared.ldap.cursor.Tuple;


/**
 * Abstract class managing the object for index entries.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @param <V> The value stored in the Tuple, associated key for the object
 * @param <ID> The ID of the object
 * @param <O> The associated object
 */
public abstract class AbstractIndexEntry<V, O, ID> implements IndexEntry<V, O, ID>
{
    /** The referenced object if loaded from the store */
    private O object;

    /**
     * Creates an instance of AbstractIndexEntry
     * 
     * @param object The interned object
     */
    protected AbstractIndexEntry( O object )
    {
        this.object = object;
    }
    
    
    /**
     * {@inheritDoc}
     */
    public abstract V getValue();

    
    /**
     * {@inheritDoc}
     */
    public abstract void setValue( V value );

    
    /**
     * {@inheritDoc}
     */
    public abstract ID getId();

    
    /**
     * {@inheritDoc}
     */
    public abstract void setId( ID id );

    
    /**
     * {@inheritDoc}
     */
    public O getObject()
    {
        return object;
    }

    
    /**
     * {@inheritDoc}
     */
    public abstract Tuple<?, ?> getTuple();

    
    /**
     * {@inheritDoc}
     */
    public void setObject( O object )
    {
        this.object = object;
    }

    
    /**
     * {@inheritDoc}
     */
    public void clear()
    {
        object = null;
    }

    
    /**
     * {@inheritDoc}
     */
    public void copy( IndexEntry<V, O, ID> entry )
    {
        object = entry.getObject();
    }
}