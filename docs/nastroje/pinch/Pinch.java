import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.lang.reflect.Method;

/** Dvouprstové štípnutí kolem (cx, cy): z rozestupu `from` na `to` px za `ms` ms. Běží přes app_process jako shell. */
public class Pinch {
    public static void main(String[] args) throws Exception {
        float cx = Float.parseFloat(args[0]), cy = Float.parseFloat(args[1]);
        float from = Float.parseFloat(args[2]), to = Float.parseFloat(args[3]);
        int ms = Integer.parseInt(args[4]);
        Class<?> im;
        Object manager;
        try {
            im = Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException e) {
            im = Class.forName("android.hardware.input.InputManager");
        }
        manager = im.getDeclaredMethod("getInstance").invoke(null);
        Method inject = im.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
        long down = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
        for (int i = 0; i < 2; i++) {
            props[i] = new MotionEvent.PointerProperties();
            props[i].id = i;
            props[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coords[i] = new MotionEvent.PointerCoords();
            coords[i].pressure = 1f;
            coords[i].size = 1f;
        }
        place(coords, cx, cy, from);
        send(inject, manager, MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, 1, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0));
        send(inject, manager, MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0));
        int steps = Math.max(1, ms / 16);
        for (int s = 1; s <= steps; s++) {
            Thread.sleep(16);
            place(coords, cx, cy, from + (to - from) * s / steps);
            send(inject, manager, MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, 2, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0));
        }
        if (args.length > 5) Thread.sleep(Integer.parseInt(args[5]));
        send(inject, manager, MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0));
        send(inject, manager, MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 1, props, coords, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0));
    }

    private static void place(MotionEvent.PointerCoords[] c, float cx, float cy, float gap) {
        c[0].x = cx; c[0].y = cy - gap / 2;
        c[1].x = cx; c[1].y = cy + gap / 2;
    }

    private static void send(Method inject, Object manager, MotionEvent event) throws Exception {
        // 2 = INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
        inject.invoke(manager, event, 2);
        event.recycle();
    }
}
