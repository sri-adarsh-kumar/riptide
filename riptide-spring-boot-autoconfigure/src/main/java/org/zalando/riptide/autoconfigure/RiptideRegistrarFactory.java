package org.zalando.riptide.autoconfigure;

@FunctionalInterface
interface RiptideRegistrarFactory {

    RiptideRegistrar create(Registry registry, RiptideProperties rawProperties,
            RiptideProperties effectiveProperties);

}
