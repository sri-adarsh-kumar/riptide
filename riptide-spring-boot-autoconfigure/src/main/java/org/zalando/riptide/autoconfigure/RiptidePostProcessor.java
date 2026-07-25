package org.zalando.riptide.autoconfigure;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import static org.springframework.boot.context.properties.source.ConfigurationPropertySources.from;

final class RiptidePostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {

    private RiptideProperties properties;
    private RiptideProperties rawProperties;
    private RiptideRegistrarFactory registrarFactory;

    RiptidePostProcessor(final RiptideRegistrarFactory registrarFactory) {
        this.registrarFactory = registrarFactory;
    }

    @Override
    public void setEnvironment(final Environment environment) {
        final Iterable<ConfigurationPropertySource> sources =
                from(((ConfigurableEnvironment) environment).getPropertySources());
        final Binder binder = new Binder(sources,
                new PropertySourcesPlaceholdersResolver(environment));

        this.rawProperties = binder.bindOrCreate("riptide", RiptideProperties.class);
        this.properties = Defaulting.withDefaults(rawProperties);
    }

    @Override
    public void postProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry) {
        final RiptideRegistrar registrar = registrarFactory.create(new Registry(registry), rawProperties, properties);
        registrar.register();
    }

    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
        final DefaultRiptideConfigurer configurer = new DefaultRiptideConfigurer(beanFactory, properties);
        configurer.register();
    }

}
