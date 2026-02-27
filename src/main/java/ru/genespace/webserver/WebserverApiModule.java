package ru.genespace.webserver;

import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;

public class WebserverApiModule extends ServletModule
{
    @Override
    protected void configureServlets()
    {
        serve( "/webserver/*" ).with( WebserverController.class );
        bind( WebserverController.class ).in( Scopes.SINGLETON );
    }


}
