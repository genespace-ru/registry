package ru.genespace.registry;

import com.codahale.metrics.jmx.JmxReporter;
import com.developmentontheedge.be5.modules.core.CoreModule;
import com.developmentontheedge.be5.modules.core.CoreServletModule;
import com.developmentontheedge.be5.modules.monitoring.MetricsModule;
import com.developmentontheedge.be5.web.WebModule;
import com.developmentontheedge.be5.server.servlet.Be5ServletListener;
import com.developmentontheedge.be5.server.servlet.TemplateModule;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import ru.genespace.github.GitHubModule;
import ru.genespace.webserver.WebserverApiModule;

import static com.developmentontheedge.be5.modules.monitoring.Metrics.METRIC_REGISTRY;


public class AppGuiceServletConfig extends Be5ServletListener
{
    @Override
    protected Injector getInjector()
    {
        return Guice.createInjector(getStage(), new AppModule());
    }

    private static class AppModule extends AbstractModule
    {
        @Override
        protected void configure()
        {
            install(new CoreModule());
            install(new CoreServletModule());
            install(new WebModule());
            install(new TemplateModule());
            install(new MetricsModule());
            install( new GitHubModule() );
            install( new WebserverApiModule() );

            final JmxReporter jmxReporter = JmxReporter.forRegistry(METRIC_REGISTRY).build();
            jmxReporter.start();

        }
    }
}
