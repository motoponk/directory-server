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
package org.apache.ldap.server.protocol;


import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;

import org.apache.apseda.listener.ClientKey;
import org.apache.apseda.protocol.AbstractNoReplyHandler;


/**
 * A no reply protocol handler implementation for LDAP {@link
 * org.apache.ldap.common.message.UnbindRequest}s.
 *
 * @author <a href="mailto:directory-dev@incubator.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class UnbindHandler extends AbstractNoReplyHandler
{
    public void handle( ClientKey key, Object request )
    {
        SessionRegistry registry = SessionRegistry.getSingleton();

        try
        {
            InitialLdapContext ictx = SessionRegistry.getSingleton()
                    .getInitialLdapContext( key, null, false );
            if ( ictx != null )
            {
                ictx.close();
            }
            registry.terminateSession( key );
            registry.remove( key );
        }
        catch ( NamingException e )
        {
            // @todo not much we can do here but monitoring would be good
            e.printStackTrace();
        }
    }
}
