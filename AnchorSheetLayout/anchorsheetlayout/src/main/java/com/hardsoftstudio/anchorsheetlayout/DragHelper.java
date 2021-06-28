package com.hardsoftstudio.anchorsheetlayout;

import ohos.agp.components.*;
import ohos.app.Context;
import ohos.multimodalinput.event.ManipulationEvent;
import ohos.multimodalinput.event.TouchEvent;
import java.util.Arrays;

public class DragHelper {

    /**
     * A null/invalid pointer ID.
     */
    public static final int INVALID_POINTER = -1;

    /**
     * A component is not currently being dragged or animating as a result of a fling/snap.
     */
    public static final int STATE_IDLE = 0;
    /**
     * A component is currently being dragged. The position is currently changing as a result
     * of user input or simulated user input.
     */
    public static final int STATE_DRAGGING = 1;
    /**
     * A component is currently settling into place as a result of a fling or
     * predefined non-interactive motion.
     */
    public static final int STATE_SETTLING = 2;

    /** Current drag state; idle, dragging or settling */
    private int mDragState;

    /** Distance to travel before a drag may begin */
    private final int mTouchSlop;

    /** Last known position/pointer tracking */
    private int mActivePointerId = INVALID_POINTER;
    private float[] mInitialMotionX;
    private float[] mInitialMotionY;
    private float[] mLastMotionX;
    private float[] mLastMotionY;
    private int mPointersDown;
    // recent pointer offset values
    private float mDeltaX = 0.0f;
    private float mDeltaY = 0.0f;

    private VelocityDetector mVelocityDetector;
    private final float mMaxVelocity;
    private final float mMinVelocity;
    private final DragHelper.Callback mCallback;
    private Component mCapturedView;
    private final ComponentContainer mParentView;

    /**
     * Factory method to create a new ViewDragHelper.
     *
     * @param forParent Parent view to monitor
     * @param cb Callback to provide information and receive events
     * @return a new ViewDragHelper instance
     */
    public static DragHelper create(@NonNull ComponentContainer forParent, @NonNull DragHelper.Callback cb) {
        return new DragHelper(forParent.getContext(), forParent, cb);
    }

    /**
     * Use ViewDragHelper.create() to get a new instance.
     *
     * @param context Context to initialize config-dependent params from
     * @param forParent Parent view to monitor
     */
    private DragHelper(@NonNull Context context, @NonNull ComponentContainer forParent, @NonNull DragHelper.Callback cb) {
        if (forParent == null) {
            throw new IllegalArgumentException("Parent view may not be null");
        } else if (cb == null) {
            throw new IllegalArgumentException("Callback may not be null");
        } else {
            this.mParentView = forParent;
            this.mCallback = cb;
            this.mTouchSlop = 24;
            this.mMaxVelocity = 2000;
            this.mMinVelocity =75;
        }
    }

    /**
     * Return the currently configured minimum velocity. Callback methods accepting a velocity will receive
     * zero as a velocity value if the real detected velocity was below this threshold.
     *
     * @return the minimum velocity that will be detected
     */
    public float getMinVelocity() {
        return this.mMinVelocity;
    }

    /**
     * Return the currently configured maximum velocity. Callback methods accepting a velocity will receive
     * this value as a velocity value if the real detected velocity was above this threshold.
     *
     * @return the minimum velocity that will be detected
     */
    public float getMaxVelocity(){
        return this.mMaxVelocity;

    }

    /**
     * @return CallBack object
     */
    public Callback getCallback(){
        return this.mCallback;
    }

    /**
     * Capture a specific child view for dragging within the parent.
     *
     * @param childView Child view to capture
     * @param activePointerId ID of the pointer that is dragging the captured child view
     */
    public void captureChildView(@NonNull Component childView, int activePointerId) {
        if (childView.getComponentParent() != this.mParentView) {
            throw new IllegalArgumentException("captureChildView: parameter must be a descendant of the ViewDragHelper's tracked parent view (" + this.mParentView + ")");
        } else {
            this.mCapturedView = childView;
            this.mActivePointerId = activePointerId;
            this.setDragState(1);
        }
    }

    /**
     * The result of a call to this method is equivalent to
     * {@link #processTouchEvent(TouchEvent)} receiving an ACTION_CANCEL event.
     */
    public void cancel() {
        this.mActivePointerId = -1;
        this.clearMotionHistory();
        if (this.mVelocityDetector != null) {
            this.mVelocityDetector.clear();
            this.mVelocityDetector = null;
        }

    }

    /**
     * Clamp the magnitude of value for absMin and absMax.
     * If the value is below the minimum, it will be clamped to zero.
     * If the value is above the maximum, it will be clamped to the maximum.
     *
     * @param value Value to clamp
     * @param absMin Absolute value of the minimum significant value to return
     * @param absMax Absolute value of the maximum value to return
     * @return The clamped value with the same sign as <code>value</code>
     */
    private float clampMag(float value, float absMin, float absMax) {
        float absValue = Math.abs(value);
        if (absValue < absMin) {
            return 0.0F;
        } else if (absValue > absMax) {
            return value > 0.0F ? absMax : -absMax;
        } else {
            return value;
        }
    }

    /**
     * Invokes Callback and sets the Drag State to Idle.
     */
    private void dispatchViewReleased(float xvel, float yvel) {
        this.mCallback.onViewReleased(this.mCapturedView, xvel, yvel,this.mDeltaX,this.mDeltaY);
        if (this.mDragState == 1) {
            this.setDragState(0);
        }
    }

    private void clearMotionHistory() {
        if (this.mInitialMotionX != null) {
            Arrays.fill(this.mInitialMotionX, 0.0F);
            Arrays.fill(this.mInitialMotionY, 0.0F);
            Arrays.fill(this.mLastMotionX, 0.0F);
            Arrays.fill(this.mLastMotionY, 0.0F);
            this.mPointersDown = 0;
        }
    }

    private void clearMotionHistory(int pointerId) {
        if (mInitialMotionX == null || !isPointerDown(pointerId)) {
            return;
        }
        mInitialMotionX[pointerId] = 0;
        mInitialMotionY[pointerId] = 0;
        mLastMotionX[pointerId] = 0;
        mLastMotionY[pointerId] = 0;
        mPointersDown &= ~(1 << pointerId);
    }

    private void ensureMotionHistorySizeForId(int pointerId) {
        if (this.mInitialMotionX == null || this.mInitialMotionX.length <= pointerId) {
            float[] imx = new float[pointerId + 1];
            float[] imy = new float[pointerId + 1];
            float[] lmx = new float[pointerId + 1];
            float[] lmy = new float[pointerId + 1];
            if (this.mInitialMotionX != null) {
                System.arraycopy(this.mInitialMotionX, 0, imx, 0, this.mInitialMotionX.length);
                System.arraycopy(this.mInitialMotionY, 0, imy, 0, this.mInitialMotionY.length);
                System.arraycopy(this.mLastMotionX, 0, lmx, 0, this.mLastMotionX.length);
                System.arraycopy(this.mLastMotionY, 0, lmy, 0, this.mLastMotionY.length);
            }
            this.mInitialMotionX = imx;
            this.mInitialMotionY = imy;
            this.mLastMotionX = lmx;
            this.mLastMotionY = lmy;
        }
    }

    private void saveInitialMotion(float x, float y, int pointerId) {
        this.ensureMotionHistorySizeForId(pointerId);
        this.mInitialMotionX[pointerId] = this.mLastMotionX[pointerId] = x;
        this.mInitialMotionY[pointerId] = this.mLastMotionY[pointerId] = y;
        this.mPointersDown |= 1 << pointerId;
    }

    private void saveLastMotion(ManipulationEvent ev) {
        int pointerCount = ev.getPointerCount();
        for(int i = 0; i < pointerCount; ++i) {
            int pointerId = ev.getPointerId(i);
            if (this.isValidPointerForActionMove(pointerId)) {
                float x = getTouchX((TouchEvent) ev,i);
                float y = getTouchY((TouchEvent) ev,i);
                this.mLastMotionX[pointerId] = x;
                this.mLastMotionY[pointerId] = y;
            }
        }
    }

    /**
     * Check if the given pointer ID represents a pointer that is currently down (to the best
     * of the DragHelper's knowledge).
     *
     * <p>The state used to report this information is populated by the method
     * {@link #processTouchEvent(TouchEvent)}. If one of these methods has not
     * been called for all relevant MotionEvents to track, the information reported
     * by this method may be stale or incorrect.</p>
     *
     * @param pointerId pointer ID to check; corresponds to IDs provided by MotionEvent
     * @return true if the pointer with the given ID is still down
     */
    public boolean isPointerDown(int pointerId) {
        return (this.mPointersDown & 1 << pointerId) != 0;
    }

    void setDragState(int state) {
        if (this.mDragState != state) {
            this.mDragState = state;
            this.mCallback.onViewDragStateChanged(state);
            if (this.mDragState == 0) {
                this.mCapturedView = null;
            }
        }

    }

    /**
     * Attempt to capture the view with the given pointer ID. The callback will be involved.
     * This will put us into the "dragging" state. If we've already captured this view with
     * this pointer this method will immediately return true without consulting the callback.
     *
     * @param toCapture View to capture
     * @param pointerId Pointer to capture with
     * @return true if capture was successful
     */
    boolean tryCaptureViewForDrag(Component toCapture, int pointerId) {
        if (toCapture == this.mCapturedView && this.mActivePointerId == pointerId) {
            return true;
        } else if (toCapture != null && this.mCallback.tryCaptureView(toCapture, pointerId)) {
            this.mActivePointerId = pointerId;
            this.captureChildView(toCapture, pointerId);
            return true;
        } else {
            return false;
        }
    }

    private void saveDeltaXY(float dx ,float dy){
        this.mDeltaX = dx;
        this.mDeltaY = dy;
    }

    /**
     * Process a touch event. This method will dispatch callback events
     * as needed before returning.
     * @param ev The touch event received by the parent view
     */
    public void processTouchEvent(@NonNull TouchEvent ev) {
        int action = ev.getAction();
        final int actionIndex = ev.getIndex();
        if (action == TouchEvent.PRIMARY_POINT_DOWN) {
            // Reset things for a new event stream
            this.cancel();
        }
        if (this.mVelocityDetector == null) {
            this.mVelocityDetector = VelocityDetector.obtainInstance();
        }
        this.mVelocityDetector.addEvent(ev);

        switch(action) {
            case TouchEvent.PRIMARY_POINT_DOWN:{
                final float x = getTouchX(ev,0);
                final float y = getTouchY(ev,0);
                final int pointerId = ev.getPointerId(0);
                final Component toCapture = this.findTopChildUnder((int)x, (int)y);
                this.saveInitialMotion(x, y, pointerId);
                this.tryCaptureViewForDrag(toCapture, pointerId);
                break;
            }
            case TouchEvent.PRIMARY_POINT_UP: {
                if (this.mDragState == 1) {
                    this.releaseViewForPointerUp();
                }
                this.cancel();
                break;
            }
            case TouchEvent.POINT_MOVE: {
                if (this.mDragState == 1) {
                    if (this.isValidPointerForActionMove(this.mActivePointerId)) {
                        final float x = getTouchX(ev, 0);
                        final float y = getTouchY(ev, 0);
                        final int idx = (int) (x - this.mLastMotionX[this.mActivePointerId]);
                        final int idy = (int) (y - this.mLastMotionY[this.mActivePointerId]);
                        this.dragTo((int) (this.mCapturedView.getContentPositionX() + idx), (int) (this.mCapturedView.getContentPositionY() + idy), idx, idy);
                        this.saveLastMotion(ev);
                    }
                } else {
                    int pointerCount = ev.getPointerCount();
                    for (int i = 0; i < pointerCount; ++i) {
                        final int pointerId = ev.getPointerId(i);
                        if (this.isValidPointerForActionMove(pointerId)) {
                            final float x = getTouchX(ev, i);
                            final float y = getTouchY(ev, i);
                            final float dx = x - this.mInitialMotionX[pointerId];
                            final float dy = y - this.mInitialMotionY[pointerId];
                            if (this.mDragState == 1) {
                                break;
                            }
                            final Component toCapture = this.findTopChildUnder((int) x, (int) y);
                            if (this.checkTouchSlop(toCapture, dx, dy) && this.tryCaptureViewForDrag(toCapture, pointerId)) {
                                break;
                            }
                        }
                    }
                    this.saveLastMotion(ev);
                }
                break;
            }

            case TouchEvent.OTHER_POINT_DOWN: {
                final int pointerId = ev.getPointerId(actionIndex);
                final float x = getTouchX(ev,actionIndex);
                final float y = getTouchY(ev,actionIndex);
                saveInitialMotion(x, y, pointerId);
                // A DragHelper can only manipulate one view at a time.
                if (mDragState == STATE_IDLE) {
                    // If we're idle we can do anything! Treat it like a normal down event.
                    final Component toCapture = findTopChildUnder((int) x, (int) y);
                    tryCaptureViewForDrag(toCapture, pointerId);
                } else if (isCapturedViewUnder((int) x, (int) y)) {
                    // We're still tracking a captured view. If the same view is under this
                    // point, we'll swap to controlling it with this pointer instead.
                    // (This will still work if we're "catching" a settling view.)
                    tryCaptureViewForDrag(mCapturedView, pointerId);
                }
                break;
            }

            case TouchEvent.OTHER_POINT_UP: {
                final int pointerId = ev.getPointerId(actionIndex);
                if (mDragState == STATE_DRAGGING && pointerId == mActivePointerId) {
                    // Try to find another pointer that's still holding on to the captured view.
                    int newActivePointer = INVALID_POINTER;
                    final int pointerCount = ev.getPointerCount();
                    for (int i = 0; i < pointerCount; i++) {
                        final int id = ev.getPointerId(i);
                        if (id == mActivePointerId) {
                            // This one's going away, skip.
                            continue;
                        }
                        final float x = getTouchX(ev,i);
                        final float y = getTouchY(ev,i);
                        if (findTopChildUnder((int) x, (int) y) == mCapturedView &&
                                tryCaptureViewForDrag(mCapturedView, id)) {
                            newActivePointer = mActivePointerId;
                            break;
                        }
                    }
                    if (newActivePointer == INVALID_POINTER) {
                        // We didn't find another pointer still touching the view, release it.
                        releaseViewForPointerUp();
                    }
                }
                clearMotionHistory(pointerId);
                break;
            }

            case TouchEvent.CANCEL:
                if (this.mDragState == 1) {
                    this.dispatchViewReleased(0.0F, 0.0F);
                }
                this.cancel();
            default:
                break;
        }
    }

    /**
     * Check if we've crossed a reasonable touch slop for the given child view.
     * If the child cannot be dragged along the horizontal or vertical axis, motion
     * along that axis will not count toward the slop check.
     *
     * @param child Child to check
     * @param dx Motion since initial position along X axis
     * @param dy Motion since initial position along Y axis
     * @return true if the touch slop has been crossed
     */
    private boolean checkTouchSlop(Component child, float dx, float dy) {
        if (child == null) {
            return false;
        } else {
            boolean checkHorizontal = this.mCallback.getViewHorizontalDragRange(child) > 0;
            boolean checkVertical = this.mCallback.getViewVerticalDragRange(child) > 0;
            if (checkHorizontal && checkVertical) {
                return dx * dx + dy * dy > (float)(this.mTouchSlop * this.mTouchSlop);
            } else if (checkHorizontal) {
                return Math.abs(dx) > (float)this.mTouchSlop;
            } else if (checkVertical) {
                return Math.abs(dy) > (float)this.mTouchSlop;
            } else {
                return false;
            }
        }
    }

    private void releaseViewForPointerUp() {
        this.mVelocityDetector.calculateCurrentVelocity(1000);float xvel = this.clampMag(this.mVelocityDetector.getHorizontalVelocity(), this.mMinVelocity, this.mMaxVelocity);
        float yvel = this.clampMag(this.mVelocityDetector.getVerticalVelocity(), this.mMinVelocity, this.mMaxVelocity);
        this.dispatchViewReleased(xvel, yvel);
    }

    private void dragTo(int left, int top, int dx, int dy) {
        int clampedX = left;
        int clampedY = top;
        int oldLeft = (int) this.mCapturedView.getContentPositionX();
        int oldTop = (int) this.mCapturedView.getContentPositionY();
        if (dx != 0) {
            clampedX = this.mCallback.clampViewPositionHorizontal(this.mCapturedView, left, dx);
            this.mCapturedView.setContentPositionX(clampedX);
        }
        if (dy != 0) {
            clampedY = this.mCallback.clampViewPositionVertical(this.mCapturedView, top, dy);
            this.mCapturedView.setContentPositionY(clampedY);
        }
        if (dx != 0 || dy != 0) {
            int clampedDx = clampedX - oldLeft;
            int clampedDy = clampedY - oldTop;
            saveDeltaXY(clampedDx,clampedDy);
            this.mCallback.onViewPositionChanged(this.mCapturedView, clampedX, clampedY, clampedDx, clampedDy);
        }

    }

    /**
     * Determine if the currently captured view is under the given point in the
     * parent view's coordinate system. If there is no captured view this method
     * will return false.
     *
     * @param x X position to test in the parent's coordinate system
     * @param y Y position to test in the parent's coordinate system
     * @return true if the captured view is under the given point, false otherwise
     */
    public boolean isCapturedViewUnder(int x, int y) {
        return isViewUnder(mCapturedView, x, y);
    }

    /**
     * Determine if the supplied view is under the given point in the
     * parent view's coordinate system.
     *
     * @param view Child view of the parent to hit test
     * @param x X position to test in the parent's coordinate system
     * @param y Y position to test in the parent's coordinate system
     * @return true if the supplied view is under the given point, false otherwise
     */
    public boolean isViewUnder(Component view, int x, int y) {
        if (view == null) {
            return false;
        }
        return x >= view.getLeft() &&
                x < view.getRight() &&
                y >= view.getTop() &&
                y < view.getBottom();
    }

    /**
     * Find the topmost child under the given point within the parent view's coordinate system.
     * The child order is determined using {@link Callback#getOrderedChildIndex(int)}.
     *
     * @param x X position to test in the parent's coordinate system
     * @param y Y position to test in the parent's coordinate system
     * @return The topmost child view under (x, y) or null if none found.
     */
    @Nullable
    public Component findTopChildUnder(int x, int y) {
        int childCount = this.mParentView.getChildCount();
        for(int i = childCount - 1; i >= 0; --i) {
            Component child = this.mParentView.getComponentAt(this.mCallback.getOrderedChildIndex(i));
            if (x >= child.getContentPositionX() && x < child.getRight() && y >= child.getContentPositionY() && y < child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    private boolean isValidPointerForActionMove(int pointerId) {
        return this.isPointerDown(pointerId);
    }

    /**
     * A Callback is used as a communication channel with the ViewDragHelper back to the
     * parent view using it. <code>on*</code>methods are invoked on significant events and several
     * accessor methods are expected to provide the ViewDragHelper with more information
     * about the state of the parent view upon request. The callback also makes decisions
     * governing the range and drag ability of child views.
     */
    public abstract static class Callback {
        public Callback() {
        }
        /**
         * Called when the drag state changes. See the <code>STATE_*</code> constants
         * for more information.
         *
         * @param state The new drag state
         *
         * @see #STATE_IDLE
         * @see #STATE_DRAGGING
         * @see #STATE_SETTLING
         */
        public void onViewDragStateChanged(int state) {
        }

        /**
         * Called when the captured view's position changes as the result of a drag or settle.
         *
         * @param changedView View whose position changed
         * @param left New X coordinate of the left edge of the view
         * @param top New Y coordinate of the top edge of the view
         * @param dx Change in X position from the last call
         * @param dy Change in Y position from the last call
         */
        public void onViewPositionChanged(@NonNull Component changedView, int left, int top, int dx, int dy) {
        }

        /**
         * Called when the child view is no longer being actively dragged.
         * The fling velocity is also supplied, if relevant. The velocity values may
         * be clamped to system minimums or maximums.
         *
         * @param releasedChild The captured child view now being released
         * @param xvel X velocity of the pointer as it left the screen in pixels per second.
         * @param yvel Y velocity of the pointer as it left the screen in pixels per second.
         * @param dx Recent X offset of the pointer
         * @param dy Recent Y offset of the pointer
         */
        public void onViewReleased(@NonNull Component releasedChild, float xvel, float yvel, float dx, float dy) {
        }

        /**
         * Called to determine the Z-order of child views.
         *
         * @param index the ordered position to query for
         * @return index of the view that should be ordered at position <code>index</code>
         */
        public int getOrderedChildIndex(int index) {
            return index;
        }

        /**
         * Return the magnitude of a draggable child view's horizontal range of motion in pixels.
         * This method should return 0 for views that cannot move horizontally.
         *
         * @param child Child view to check
         * @return range of horizontal motion in pixels
         */
        public int getViewHorizontalDragRange(@NonNull Component child) {
            return 0;
        }

        /**
         * Return the magnitude of a draggable child view's vertical range of motion in pixels.
         * This method should return 0 for views that cannot move vertically.
         *
         * @param child Child view to check
         * @return range of vertical motion in pixels
         */
        public int getViewVerticalDragRange(@NonNull Component child) {
            return 0;
        }

        /**
         * Called when the user's input indicates that they want to capture the given child view
         * with the pointer indicated by pointerId. The callback should return true if the user
         * is permitted to drag the given view with the indicated pointer.
         *
         * @param child Child the user is attempting to capture
         * @param pointerId ID of the pointer attempting the capture
         * @return true if capture should be allowed, false otherwise
         */
        public abstract boolean tryCaptureView(@NonNull Component child, int pointerId);

        /**
         * Restrict the motion of the dragged child view along the horizontal axis.
         * The default implementation does not allow horizontal motion; the extending
         * class must override this method and provide the desired clamping.
         *
         *
         * @param child Child view being dragged
         * @param left Attempted motion along the X axis
         * @param dx Proposed change in position for left
         * @return The new clamped position for left
         */
        public int clampViewPositionHorizontal(@NonNull Component child, int left, int dx) {
            return 0;
        }

        /**
         * Restrict the motion of the dragged child view along the vertical axis.
         * The default implementation does not allow vertical motion; the extending
         * class must override this method and provide the desired clamping.
         *
         *
         * @param child Child view being dragged
         * @param top Attempted motion along the Y axis
         * @param dy Proposed change in position for top
         * @return The new clamped position for top
         */
        public int clampViewPositionVertical(@NonNull Component child, int top, int dy) {
            return 0;
        }
    }

    public static float getTouchX(TouchEvent touchEvent, int index) {
        float x = 0;
        if (touchEvent.getPointerCount() > index) {
            x = touchEvent.getPointerPosition(index).getX();
        }
        return x;
    }

    public static float getTouchY(TouchEvent touchEvent, int index) {
        float y = 0;
        if (touchEvent.getPointerCount() > index) {
            y = touchEvent.getPointerScreenPosition(index).getY();
        }
        return y;
    }

}