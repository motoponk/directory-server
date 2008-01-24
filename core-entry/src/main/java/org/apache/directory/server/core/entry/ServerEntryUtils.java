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
package org.apache.directory.server.core.entry;

import java.util.Iterator;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeIdentifierException;

import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.constants.SchemaConstants;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.AttributeType;

/**
 * A helper class used to manipulate Entries, Attributes and Values.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class ServerEntryUtils
{
    /**
     * Convert a ServerEntry into a AttributesImpl. The DN is lost
     * during this conversion, as the Attributes object does not store
     * this element.
     *
     * @return An instance of a AttributesImpl() object
     */
    public static Attributes toAttributesImpl( ServerEntry entry )
    {
        if ( entry == null )
        {
            return null;
        }
        
        Attributes attributes = new AttributesImpl();

        for ( AttributeType attributeType:entry.getAttributeTypes() )
        {
            ServerAttribute attr = entry.get( attributeType );
            
            // Deal with a special case : an entry without any ObjectClass
            if ( attributeType.getOid() == SchemaConstants.OBJECT_CLASS_AT_OID )
            {
                if ( attr.size() == 0 )
                {
                    // We don't have any objectClass, just dismiss this element
                    continue;
                }
            }
            
            Attribute attribute = new AttributeImpl( attributeType.getName() );
            
            for ( Iterator<ServerValue<?>> iter = attr.iterator(); iter.hasNext();)
            {
                ServerValue<?> value = iter.next();
                attribute.add( value.get() );
            }
            
            attributes.put( attribute );
        }
        
        return attributes;
    }

    
    /**
     * Convert a BasicAttribute or a AttributeImpl to a ServerAtribute
     *
     * @param attributes the BasicAttributes or AttributesImpl instance to convert
     * @param registries The registries, needed ro build a ServerEntry
     * @param dn The DN which is needed by the ServerEntry 
     * @return An instance of a ServerEntry object
     * 
     * @throws InvalidAttributeIdentifierException If we had an incorrect attribute
     */
    public static ServerAttribute toServerAttribute( Attribute attribute, AttributeType attributeType )
            throws InvalidAttributeIdentifierException
    {
        if ( attribute == null )
        {
            return null;
        }
        
        try 
        {
            ServerAttribute serverAttribute = new DefaultServerAttribute( attributeType );
        
            for ( NamingEnumeration<?> values = attribute.getAll(); values.hasMoreElements(); )
            {
                Object value = values.nextElement();
                
                if ( value instanceof String )
                {
                    serverAttribute.add( (String)value );
                }
                else if ( value instanceof byte[] )
                {
                    serverAttribute.add( (byte[])value );
                }
                else
                {
                    return null;
                }
            }
            
            return serverAttribute;
        }
        catch ( NamingException ne )
        {
            return null;
        }
    }
    

    /**
     * Convert a BasicAttributes or a AttributesImpl to a ServerEntry
     *
     * @param attributes the BasicAttributes or AttributesImpl instance to convert
     * @param registries The registries, needed ro build a ServerEntry
     * @param dn The DN which is needed by the ServerEntry 
     * @return An instance of a ServerEntry object
     * 
     * @throws InvalidAttributeIdentifierException If we get an invalid attribute
     */
    public static ServerEntry toServerEntry( Attributes attributes, LdapDN dn, Registries registries ) 
            throws InvalidAttributeIdentifierException
    {
        if ( ( attributes instanceof BasicAttributes ) || ( attributes instanceof AttributesImpl ) )
        {
            try 
            {
                ServerEntry entry = new DefaultServerEntry( registries, dn );
    
                for ( NamingEnumeration<? extends Attribute> attrs = attributes.getAll(); attrs.hasMoreElements(); )
                {
                    Attribute attr = attrs.nextElement();

                    AttributeType attributeType = registries.getAttributeTypeRegistry().lookup( attr.getID() );
                    ServerAttribute serverAttribute = ServerEntryUtils.toServerAttribute( attr, attributeType );
                    
                    if ( serverAttribute != null )
                    {
                        entry.put( serverAttribute );
                    }
                }
                
                return entry;
            }
            catch ( NamingException ne )
            {
                throw new InvalidAttributeIdentifierException( ne.getMessage() );
            }
        }
        else
        {
            return null;
        }
    }


    /**
     * Convert a ServerEntry into a BasicAttributes. The DN is lost
     * during this conversion, as the Attributes object does not store
     * this element.
     *
     * @return An instance of a BasicAttributes() object
     */
    public static Attributes toBasicAttributes( ServerEntry entry )
    {
        Attributes attributes = new BasicAttributes( true );

        for ( AttributeType attributeType:entry.getAttributeTypes() )
        {
            Attribute attribute = new BasicAttribute( attributeType.getName(), true );
            
            ServerAttribute attr = entry.get( attributeType );
            
            for ( Iterator<ServerValue<?>> iter = attr.iterator(); iter.hasNext();)
            {
                ServerValue<?> value = iter.next();
                attribute.add( value );
            }
            
            attributes.put( attribute );
        }
        
        return attributes;
    }
    
    
    /**
     * Convert a ServerAttributeEntry into a BasicAttribute.
     *
     * @return An instance of a BasicAttribute() object
     */
    public static Attribute toBasicAttribute( ServerAttribute attr )
    {
        Attribute attribute = new BasicAttribute( attr.getUpId(), false );

        for ( ServerValue<?> value:attr )
        {
            attribute.add( value.get() );
        }
        
        return attribute;
    }


    /**
     * Convert a ServerAttributeEntry into a AttributeImpl.
     *
     * @return An instance of a BasicAttribute() object
     */
    public static Attribute toAttributeImpl( ServerAttribute attr )
    {
        Attribute attribute = new AttributeImpl( attr.getUpId() );

        for ( ServerValue<?> value:attr )
        {
            attribute.add( value.get() );
        }
        
        return attribute;
    }


    /**
     * Gets the target entry as it would look after a modification operation 
     * was performed on it.
     * 
     * @param mod the modification
     * @param entry the source entry that is modified
     * @return the resultant entry after the modification has taken place
     * @throws NamingException if there are problems accessing attributes
     */
    public static ServerEntry getTargetEntry( ModificationItemImpl mod, ServerEntry entry, Registries registries ) throws NamingException
    {
        ServerEntry targetEntry = ( ServerEntry ) entry.clone();
        int modOp = mod.getModificationOp();
        String id = mod.getAttribute().getID();
        AttributeType attributeType = registries.getAttributeTypeRegistry().lookup( id );
        
        switch ( modOp )
        {
            case ( DirContext.REPLACE_ATTRIBUTE  ):
                targetEntry.put( toServerAttribute( mod.getAttribute(), attributeType ) );
                break;
                
            case ( DirContext.REMOVE_ATTRIBUTE  ):
                ServerAttribute toBeRemoved = toServerAttribute( mod.getAttribute(), attributeType );

                if ( toBeRemoved.size() == 0 )
                {
                    targetEntry.remove( id );
                }
                else
                {
                    ServerAttribute existing = targetEntry.get( id );

                    if ( existing != null )
                    {
                        for ( ServerValue<?> value:toBeRemoved )
                        {
                            existing.remove( value );
                        }
                    }
                }
                break;
                
            case ( DirContext.ADD_ATTRIBUTE  ):
                ServerAttribute combined = new DefaultServerAttribute( id, attributeType );
                ServerAttribute toBeAdded = toServerAttribute( mod.getAttribute(), attributeType );
                ServerAttribute existing = entry.get( id );

                if ( existing != null )
                {
                    for ( ServerValue<?> value:existing )
                    {
                        combined.add( value );
                    }
                }

                for ( ServerValue<?> value:toBeAdded )
                {
                    combined.add( value );
                }
                
                targetEntry.put( combined );
                break;
                
            default:
                throw new IllegalStateException( "undefined modification type: " + modOp );
        }

        return targetEntry;
    }


    /**
     * Creates a new attribute which contains the values representing the union
     * of two attributes. If one attribute is null then the resultant attribute
     * returned is a copy of the non-null attribute. If both are null then we
     * cannot determine the attribute ID and an {@link IllegalArgumentException}
     * is raised.
     * 
     * @param attr0 the first attribute
     * @param attr1 the second attribute
     * @return a new attribute with the union of values from both attribute
     *         arguments
     * @throws NamingException if there are problems accessing attribute values
     */
    public static ServerAttribute getUnion( ServerAttribute attr0, ServerAttribute attr1 ) throws NamingException
    {
        if ( attr0 == null && attr1 == null )
        {
            throw new IllegalArgumentException( "Cannot figure out attribute ID if both args are null" );
        }
        else if ( attr0 == null )
        {
            return (ServerAttribute)attr1.clone();
        }
        else if ( attr1 == null )
        {
            return (ServerAttribute)attr0.clone();
        }
        else if ( !attr0.getType().equals( attr1.getType() ) )
        {
            throw new IllegalArgumentException( "Cannot take union of attributes with different IDs!" );
        }

        ServerAttribute attr = (ServerAttribute)attr0.clone();

        if ( attr0 != null )
        {
            for ( ServerValue<?> value:attr1 )
            {
                attr.add( value );
            }
        }

        return attr;
    }
}
