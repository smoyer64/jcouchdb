package org.jcouchdb.db;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.log4j.Logger;
import org.jcouchdb.exception.CouchDBException;
import org.jcouchdb.util.ExceptionWrapper;

/**
 * Default implementation of the {@link Server} interface.
 *
 * @author shelmberger
 *
 */
public class ServerImpl implements Server
{
    protected static Logger log = Logger.getLogger(ServerImpl.class);

    private HostConfiguration hostConfiguration;

    private HttpClient httpClient = new HttpClient();

    private List<ServerEventHandler> serverEventHandlers;

    public ServerImpl(String host)
    {
        this(host, DEFAULT_PORT);
    }

    public ServerImpl(String host, int port)
    {
        this.hostConfiguration = new HostConfiguration();
        this.hostConfiguration.setHost(host, port);
        httpClient.setHostConfiguration(hostConfiguration);
    }

    /**
     * {@inheritDoc}
     */
    public List<String> listDatabases()
    {
        Response resp = get("/_all_dbs");
        if (!resp.isOk())
        {
            throw new CouchDBException("Error listing databases: "+resp);
        }
        return resp.getContentAsList();
    }

    /**
     * {@inheritDoc}
     */
    public boolean createDatabase(String name)
    {
        Response resp = put("/"+name+"/");
        if (resp.isOk())
        {
            return true;
        }
        else
        {
            if (resp.getCode() == 409 || resp.getCode() == 500)
            {
                return false;
            }
            else
            {
                throw new CouchDBException("Error creating database: "+resp);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void deleteDatabase(String name)
    {
        Response resp = delete("/"+name+"/");
        if (!resp.isOk())
        {
            throw new CouchDBException("Cannot delete database "+name+": "+resp);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Response get(String uri)
    {
        try
        {
            if (log.isDebugEnabled())
            {
                log.debug("GET "+uri);
            }
            fireBeforeEvent("get", uri, null);

            GetMethod method = new GetMethod(uri);
            int code = httpClient.executeMethod(method);
            Response response = new Response(code, method.getResponseBody());
            fireAfterEvent("get", uri, null, response);
            return response;
        }
        catch (HttpException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (IOException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (Exception e)
        {
            throw ExceptionWrapper.wrap(e);
        }

    }

    /**
     * {@inheritDoc}
     */
    public Response put(String uri)
    {
        return put(uri,null);
    }

    /**
     * {@inheritDoc}
     */
    public Response put(String uri, String body)
    {
        try
        {
            if (log.isDebugEnabled())
            {
                log.debug("PUT "+uri+", body = "+body);
            }
            fireBeforeEvent("put", uri, body);
            PutMethod putMethod = new PutMethod(uri);
            if (body != null)
            {
                putMethod.setRequestEntity(new StringRequestEntity(body, "application/json", "UTF-8"));
            }
            int code = httpClient.executeMethod(putMethod);
            Response response = new Response(code, putMethod.getResponseBody());
            fireAfterEvent("put", uri, body, response);
            return response;
        }
        catch (HttpException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (IOException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (Exception e)
        {
            throw ExceptionWrapper.wrap(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Response put(String uri, byte[] body, String contentType)
    {
        try
        {
            if (log.isDebugEnabled())
            {
                log.debug("PUT "+uri+", body = "+body);
            }

            fireBeforeEvent("put", uri, body);

            PutMethod putMethod = new PutMethod(uri);
            if (body != null)
            {
                putMethod.setRequestEntity(new ByteArrayRequestEntity(body, contentType));
            }
            int code = httpClient.executeMethod(putMethod);
            Response response = new Response(code, putMethod.getResponseBody());

            fireAfterEvent("put", uri, body, response);

            return response;
        }
        catch (HttpException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (IOException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (Exception e)
        {
            throw ExceptionWrapper.wrap(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Response post(String uri, String body)
    {
        try
        {
            if (log.isDebugEnabled())
            {
                log.debug("POST "+uri+", body = "+body);
            }

            fireBeforeEvent("post", uri, body);

            PostMethod postMethod = new PostMethod(uri);
            postMethod.setRequestEntity(new StringRequestEntity(body, "application/json", "UTF-8"));
            int code = httpClient.executeMethod(postMethod);
            Response response = new Response(code, postMethod.getResponseBody());

            fireAfterEvent("post", uri, body, response);

            return response;
        }
        catch (HttpException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (IOException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (Exception e)
        {
            throw ExceptionWrapper.wrap(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Response delete(String uri)
    {
        try
        {
            if (log.isDebugEnabled())
            {
                log.debug("DELETE "+uri);
            }

            fireBeforeEvent("delete", uri, null);

            DeleteMethod deleteMethod = new DeleteMethod(uri);
            int code = httpClient.executeMethod(deleteMethod);
            Response response = new Response(code, deleteMethod.getResponseBody());

            fireAfterEvent("delete", uri, null, response);

            return response;
        }
        catch (HttpException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (IOException e)
        {
            throw ExceptionWrapper.wrap(e);
        }
        catch (Exception e)
        {
            throw ExceptionWrapper.wrap(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setCredentials(AuthScope authScope, Credentials credentials)
    {
        httpClient.getState().setCredentials(authScope, credentials);
    }

    public void addServerEventHandler(ServerEventHandler serverEventHandler)
    {
        if (serverEventHandlers == null)
        {
            serverEventHandlers = new ArrayList<ServerEventHandler>();
        }
        serverEventHandlers.add(serverEventHandler);
    }

    private void fireBeforeEvent(String method, String uri, Object data) throws Exception
    {
        if (serverEventHandlers != null)
        {
            for (ServerEventHandler handler : serverEventHandlers)
            {
                handler.executing(method, uri, data);
            }
        }
    }

    private void fireAfterEvent(String method, String uri, Object data, Response response)
    {
        if (serverEventHandlers != null)
        {
            for (ServerEventHandler handler : serverEventHandlers)
            {
                try
                {
                    handler.executed(method, uri, data, response);
                }
                catch(Exception e)
                {
                    log.error("ignorin error in server event handler method",e);
                }
            }
        }
    }
}