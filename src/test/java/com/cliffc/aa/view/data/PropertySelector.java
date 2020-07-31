package com.cliffc.aa.view.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PropertySelector<T extends Properties.Provider> {

    private final Collection<T> objects;

    public PropertySelector(Collection<T> objects) {
        this.objects = objects;
    }

    public T selectSingle(PropertyMatcher matcher) {

        for (T t : objects) {
            Property p = t.getProperties().selectSingle(matcher);
            if (p != null) {
                return t;
            }
        }

        return null;
    }

    public List<T> selectMultiple(PropertyMatcher matcher) {
        List<T> result = new ArrayList<>();

        for (T t : objects) {
            Property p = t.getProperties().selectSingle(matcher);
            if (p != null) {
                result.add(t);
            }
        }

        return result;
    }
}
