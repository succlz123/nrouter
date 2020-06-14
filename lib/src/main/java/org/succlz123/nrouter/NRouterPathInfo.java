package org.succlz123.nrouter;

public class NRouterPathInfo {
    public static final int TYPE_ACTIVITY = 1;

    public int type;

    public String path;

    public String className;

    public String simpleClassName;

    public AbsRouterMapper routerMapper;

    public Object obtainDestination() {
        return routerMapper.obtainInstance(className);
    }
}
