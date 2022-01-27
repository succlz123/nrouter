package org.succlz123.nrouter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.SparseArray;

import androidx.core.app.ActivityCompat;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class NRouter {
    private static Context appContext;

    private ArrayList<AbsRouterMapper> allMapper = new ArrayList<>();
    private HashMap<String, NRouterPathInfo> allRouterPathInfo = new HashMap<>();

    private NRouter() {
        register();
        initMapper();
    }

    private static NRouter instance() {
        return InstanceHolder._sInstance;
    }

    private static class InstanceHolder {
        private static final NRouter _sInstance = new NRouter();
    }

    public static void init(Context context) {
        appContext = context;
    }

    public static Builder path(String path) {
        Builder builder = new Builder();
        builder.path = path;
        return builder;
    }

    public static class Builder {
        private String path;
        private int activityFlags = -1;
        private int activityEnterAnim = -1;
        private int activityExitAnim = -1;
        private Bundle options;
        private Bundle content = new Bundle();

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder with(String key, boolean value) {
            content.putBoolean(key, value);
            return this;
        }

        public Builder with(String key, byte value) {
            content.putByte(key, value);
            return this;
        }

        public Builder with(String key, char value) {
            content.putChar(key, value);
            return this;
        }

        public Builder with(String key, short value) {
            content.putShort(key, value);
            return this;
        }

        public Builder with(String key, int value) {
            content.putInt(key, value);
            return this;
        }

        public Builder with(String key, long value) {
            content.putLong(key, value);
            return this;
        }

        public Builder with(String key, float value) {
            content.putFloat(key, value);
            return this;
        }

        public Builder with(String key, double value) {
            content.putDouble(key, value);
            return this;
        }

        public Builder with(String key, String value) {
            content.putString(key, value);
            return this;
        }

        public Builder with(String key, Character value) {
            content.putChar(key, value);
            return this;
        }

        public Builder with(String key, Parcelable value) {
            content.putParcelable(key, value);
            return this;
        }

        public Builder with(String key, Parcelable[] value) {
            content.putParcelableArray(key, value);
            return this;
        }

        public Builder with(String key, ArrayList<? extends Parcelable> value) {
            content.putParcelableArrayList(key, value);
            return this;
        }

        public Builder with(String key, SparseArray<? extends Parcelable> value) {
            content.putSparseParcelableArray(key, value);
            return this;
        }

        public Builder withIntegerArrayList(String key, ArrayList<Integer> value) {
            content.putIntegerArrayList(key, value);
            return this;
        }

        public Builder withStringArrayList(String key, ArrayList<String> value) {
            content.putStringArrayList(key, value);
            return this;
        }

        public Builder with(String key, CharSequence value) {
            content.putCharSequence(key, value);
            return this;
        }

        public Builder withCharSequenceArrayList(String key, CharSequence[] value) {
            content.putCharSequenceArray(key, value);
            return this;
        }

        public Builder withCharSequenceArrayList(String key, ArrayList<CharSequence> value) {
            content.putCharSequenceArrayList(key, value);
            return this;
        }

        public Builder with(String key, Serializable value) {
            content.putSerializable(key, value);
            return this;
        }

        public Builder with(String key, boolean[] value) {
            content.putBooleanArray(key, value);
            return this;
        }

        public Builder with(String key, byte[] value) {
            content.putByteArray(key, value);
            return this;
        }

        public Builder with(String key, char[] value) {
            content.putCharArray(key, value);
            return this;
        }

        public Builder with(String key, short[] value) {
            content.putShortArray(key, value);
            return this;
        }

        public Builder with(String key, int[] value) {
            content.putIntArray(key, value);
            return this;
        }

        public Builder with(String key, long[] value) {
            content.putLongArray(key, value);
            return this;
        }

        public Builder with(String key, float[] value) {
            content.putFloatArray(key, value);
            return this;
        }

        public Builder with(String key, double[] value) {
            content.putDoubleArray(key, value);
            return this;
        }

        public Builder with(String key, String[] value) {
            content.putStringArray(key, value);
            return this;
        }

        public Builder with(String key, Bundle value) {
            content.putBundle(key, value);
            return this;
        }

        public Builder activityOptions(Bundle value) {
            options = value;
            return this;
        }

        public Builder activityFlags(int flags) {
            this.activityFlags = flags;
            return this;
        }

        public Builder activityEnterAnim(int enterAnim) {
            this.activityEnterAnim = enterAnim;
            return this;
        }

        public Builder activityExitAnim(int exitAnim) {
            this.activityExitAnim = exitAnim;
            return this;
        }

        void open(Context context) {
            processActivity(context, 0, this, getPathInfo(this));
        }

        void open(Activity context, int requestCode) {
            processActivity(context, requestCode, this, getPathInfo(this));
        }

        <T> T open() {
            NRouterPathInfo pathInfo = getPathInfo(this);
            if (pathInfo.type == NRouterPathInfo.TYPE_ACTIVITY) {
                open(appContext);
                return null;
            }
            return processDestination(pathInfo);
        }
    }

    private static void processActivity(Context context, int requestCode, Builder builder, NRouterPathInfo pathInfo) {
        if (context == null) {
            throw new RuntimeException("NRouter - Start Activity - Context is null!");
        }
        Intent intent = new Intent();
        try {
            intent.setClass(context, Class.forName(pathInfo.className));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("NRouter - Can't find the path class!");
        }
        intent.putExtras(builder.content);
        int activityFlags = builder.activityFlags;
        if (-1 != activityFlags) {
            intent.setFlags(activityFlags);
        } else if (!(context instanceof Activity)) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            Bundle bundle = builder.options;
            if (requestCode > 0) {
                ActivityCompat.startActivityForResult((Activity) context, intent, requestCode, bundle);
            } else {
                ActivityCompat.startActivity(context, intent, bundle);
            }
            if ((0 != builder.activityEnterAnim || 0 != builder.activityExitAnim) && context instanceof Activity) {
                ((Activity) context).overridePendingTransition(builder.activityEnterAnim, builder.activityExitAnim);
            }
        });
    }

    private static <T> T processDestination(NRouterPathInfo pathInfo) {
        Object o = pathInfo.obtainDestination();
        if (o == null) {
            throw new RuntimeException("NRouter - Wtf -  path destination is not found!");
        }
        return (T) o;
    }

    private static NRouterPathInfo getPathInfo(Builder builder) {
        if (appContext == null) {
            throw new NullPointerException("NRouter - Please init NRouter first!");
        }
        if (builder.path == null || builder.path.isEmpty()) {
            throw new NullPointerException("NRouter - Path is null!");
        }
        if (NRouter.instance().allRouterPathInfo.isEmpty()) {
            throw new NullPointerException("NRouter - Init failed, Did you apply the Gradle plugin in the build.gradle?");
        }
        NRouterPathInfo pathInfo = NRouter.instance().allRouterPathInfo.get(builder.path);
        if (pathInfo == null) {
            throw new NullPointerException("NRouter - Can't find the path!");
        }
        return pathInfo;
    }

    void initMapper() {
        for (AbsRouterMapper routerMapper : allMapper) {
            List<NRouterPathInfo> pathInfo = routerMapper.getAllRouterPathInfo();
            for (NRouterPathInfo info : pathInfo) {
                this.allRouterPathInfo.put(info.path, info);
            }
        }
    }

    void register() {
        // allMapper.add(xxxMapper);
        // allMapper.add(yyyMapper);
    }
}
