package com.cliffc.aa.view.data;

public class Entity implements Properties.Provider {

    private Properties properties;

    protected Entity() {
        properties = new Properties();
    }

    Entity(Entity object) {
        properties = new Properties(object.getProperties());
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    public void internProperties() {
        properties = Properties.PropertyCache.intern(properties);
    }
}
