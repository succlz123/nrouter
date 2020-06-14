package org.succlz123.nrouter;

import java.util.ArrayList;

public abstract class AbsRouterMapper {

    public abstract ArrayList<NRouterPathInfo> getAllRouterPathInfo();

    public abstract Object obtainInstance(String className);
}