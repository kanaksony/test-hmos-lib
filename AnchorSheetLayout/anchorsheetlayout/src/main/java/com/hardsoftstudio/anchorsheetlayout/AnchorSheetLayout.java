package com.hardsoftstudio.anchorsheetlayout;

import ohos.agp.animation.Animator;
import ohos.agp.animation.AnimatorValue;
import ohos.agp.components.*;
import ohos.app.Context;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import ohos.multimodalinput.event.TouchEvent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AnchorSheetLayout extends ComponentContainer
{
    /**
     * Callback for monitoring events about bottom sheets.
     */
    public abstract static class AnchorSheetCallback {
        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomSheet The bottom sheet view.
         * @param newState    The new state. This will be one of {@link #STATE_DRAGGING},
         *                    {@link #STATE_SETTLING}, {@link #STATE_EXPANDED},
         *                    {@link #STATE_COLLAPSED}, or {@link #STATE_HIDDEN}.
         */
        public abstract void onStateChanged(Component bottomSheet, @State int newState);

        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomSheet The bottom sheet view.
         * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset
         *                    increases as this bottom sheet is moving upward. From 0 to 1 the sheet
         *                    is between collapsed and expanded states and from -1 to 0 it is
         *                    between hidden and collapsed states.
         */
        public abstract void onSlide(Component bottomSheet, float slideOffset);
    }

    private static final HiLogLabel LABEL = new HiLogLabel(HiLog.LOG_APP, 0x00201, "MY_TAG");

    /**
     * There can be only one child component
     * in case need to add more components, add a component container and place everything
     * in it
     */
    private ComponentContainer mChild;

    /**
     * The bottom sheet is dragging.
     */
    public static final int STATE_DRAGGING = 1;

    /**
     * The bottom sheet is settling.
     */
    public static final int STATE_SETTLING = 2;

    /**
     * The bottom sheet is expanded.
     */
    public static final int STATE_EXPANDED = 3;

    /**
     * The bottom sheet is collapsed.
     */
    public static final int STATE_COLLAPSED = 4;

    /**
     * The bottom sheet is hidden.
     */
    public static final int STATE_HIDDEN = 5;

    /**
     * The bottom sheet is anchor.
     */
    public static final int STATE_ANCHOR = 6;

    /**
     * The bottom sheet is forced to be hidden programmatically.
     */
    public static final int STATE_FORCE_HIDDEN = 7;

    @IntDef({
            STATE_EXPANDED,
            STATE_COLLAPSED,
            STATE_DRAGGING,
            STATE_SETTLING,
            STATE_HIDDEN,
            STATE_ANCHOR,
            STATE_FORCE_HIDDEN
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface State {
    }

    private static final float HIDE_THRESHOLD = 0.25f;

    private static final float HIDE_FRICTION = 0.1f;

    private static final float ANCHOR_THRESHOLD = 0.50f;

    private float mAnchorThreshold = ANCHOR_THRESHOLD;

    private int mPeekHeight;

    private int mMinOffset;

    private int mMaxOffset;

    private int mAnchorOffset;

    private boolean mHideable;

    private boolean mSkipCollapsed;

    @State
    private int mState = STATE_COLLAPSED;

    private DragHelper mDragHelper;

    private final int mParentHeight;

    private WeakReference<ComponentContainer> mViewRef;

    private AnchorSheetCallback mCallback;

    private VelocityDetector mVelocityTracker;

    private final List<WeakReference<Component>> mChildrenList;

    /**
     * Constructor
     *
     * @param context The {@link Context}.
     * @param attrs   The {@link AttrSet}.
     */
    public AnchorSheetLayout(Context context, AttrSet attrs) {
        super(context, attrs);
        this.mParentHeight = 1950;
        this.mMinOffset = 0;
        mAnchorOffset = (int) Math.max(mParentHeight * mAnchorThreshold, mMinOffset);
        setPeekHeight(250);
        setHideable(true);
        setSkipCollapsed(false);
        if (mDragHelper == null) {
            initDragHelper();
        }
        mChildrenList = new ArrayList<>();
    }

    private void initDragHelper(){
        DragHelper.Callback mDragCallback = new DragHelper.Callback() {
            @Override
            public boolean tryCaptureView(Component child, int pointerId) {
                if (mState == STATE_DRAGGING) {
                    return false;
                }
                return mViewRef != null && mViewRef.get() == child;
            }

            @Override
            public void onViewPositionChanged(Component changedView, int left, int top, int dx, int dy) {
                dispatchOnSlide(top);
            }

            @Override
            public void onViewDragStateChanged(int state) {
                if (state == DragHelper.STATE_DRAGGING) {
                    setStateInternal(STATE_DRAGGING);
                }
            }

            @Override
            public void onViewReleased(Component releasedChild, float xvel, float yvel, float dx, float dy) {
                int currentTop = (int) releasedChild.getContentPositionY();
                HiLog.info(LABEL, String.valueOf(dy));
                HiLog.info(LABEL, String.valueOf(yvel));
                @State int targetState;
                if (dy < 0) { // moving up
                    if (yvel == 0.f) {
                        if (Math.abs(currentTop - mMinOffset) < Math.abs(currentTop - mAnchorOffset)) {
                            targetState = STATE_EXPANDED;
                        } else if (Math.abs(currentTop - mAnchorOffset) < Math.abs(currentTop - mMaxOffset)) {
                            targetState = STATE_ANCHOR;
                        } else {
                            targetState = STATE_COLLAPSED;
                        }
                    } else {
                        if (currentTop < mAnchorOffset) {
                            targetState = STATE_EXPANDED;
                        } else {
                            targetState = STATE_ANCHOR;
                        }
                    }
                } else if (dy > 0) {
                    if (mHideable && shouldHide(releasedChild, yvel)) {
                        targetState = STATE_HIDDEN;
                    } else {
                        targetState = STATE_COLLAPSED;
                    }
                } else {
                    targetState = mState;
                }
                if (targetState != mState) startSettlingAnimation(releasedChild, targetState, (int) yvel);
            }

            @Override
            public int clampViewPositionVertical(Component child, int top, int dy) {

                return Math.min(mHideable ? mParentHeight : mMaxOffset, Math.max(mMinOffset, top));
            }

            @Override
            public int clampViewPositionHorizontal(Component child, int left, int dx) {
                return child.getLeft();
            }

            @Override
            public int getViewVerticalDragRange(Component child) {
                if (mHideable) {
                    return mParentHeight - mMinOffset;
                } else {
                    return mMaxOffset - mMinOffset;
                }
            }
        };
        mDragHelper = DragHelper.create(this, mDragCallback);
    }

    /**
     * Called When parent is trying to lay out the child
     *
     * @param comChild One and Only Child
     */
    @Override
    public void addComponent(Component comChild) {
        if (getChildCount() > 0) {
            throw new IllegalArgumentException("You may not declare more then one child");
        }
        super.addComponent(comChild);
        this.mChild = (ComponentContainer) comChild;

        switch (mState){
            case STATE_EXPANDED:
                this.mChild.setContentPositionY(mMinOffset);
                break;
            case STATE_COLLAPSED:
                this.mChild.setContentPositionY(mMaxOffset);
                break;
            case STATE_ANCHOR:
                this.mChild.setContentPositionY(mAnchorOffset);
                break;
            case STATE_HIDDEN:
            case STATE_FORCE_HIDDEN:
                this.mChild.setContentPositionY(mParentHeight);
                break;
            default:
                break;
        }
        initTouchEventListener();
        mViewRef = new WeakReference<>(this.mChild);
        mChild.setBindStateChangedListener(new BindStateChangedListener() {
            @Override
            public void onComponentBoundToWindow(Component component) {
                if(mChildrenList!=null) mChildrenList.clear();
                findScrollingChild(mChild);
            }

            @Override
            public void onComponentUnboundFromWindow(Component component) {

            }
        });
    }

    private void initTouchEventListener(){
        TouchEventListener touchEventListener = (component, event) -> {
            int action = event.getAction();
            if (action == TouchEvent.PRIMARY_POINT_DOWN) {
                reset();
                if(mState == STATE_EXPANDED){
                    for (WeakReference<Component> componentWeakReference : mChildrenList) {
                        float x = getTouchX(event,0);
                        float y = getTouchY(event,0);
                        Component mListView = componentWeakReference.get();
                        float listX1 = mListView.getContentPositionX();
                        float listX2 = mListView.getContentPositionX()+mListView.getWidth();
                        float listY1 = mListView.getContentPositionY();
                        float listY2 = mListView.getContentPositionY() + mListView.getHeight();
                        if(listX1<= x && listX2>=x && listY1<=y && listY2 >= y && mListView.canScroll(DRAG_DOWN)) return true;
                    }
                }
            }
            if (mVelocityTracker == null) {
                mVelocityTracker = VelocityDetector.obtainInstance();
            }
            mVelocityTracker.addEvent(event);
            mDragHelper.captureChildView(mChild,event.getPointerId(event.getIndex()));
            mDragHelper.processTouchEvent(event);
            if(mDragHelper == null){
                HiLog.info(LABEL,"null view drag");
            }
            return true;
        };
        this.mChild.setTouchEventListener(touchEventListener);
    }

    /**
     * Stores references of all the scrollable components present in the child
     *
     * @param component One and Only Child
     */
    private void findScrollingChild(Component component) {
        if (component.canScroll(DRAG_DOWN) || component.canScroll(DRAG_UP)) {
            mChildrenList.add(new WeakReference<>(component));
            return;
        }
        if (component instanceof ComponentContainer) {
            ComponentContainer group = (ComponentContainer) component;
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                Component scrollingChild = group.getComponentAt(i);
                findScrollingChild(scrollingChild);
            }
        }
    }

    /**
     * Sets the height of the bottom sheet when it is collapsed.
     *
     * @param peekHeight The height of the collapsed bottom sheet in pixels.
     */
    public final void setPeekHeight(int peekHeight) {
        mPeekHeight = Math.max(0, peekHeight);
        mMaxOffset = mParentHeight - peekHeight;
    }

    /**
     * Gets the height of the bottom sheet when it is collapsed.
     *
     * @return The height of the collapsed bottom sheet in pixels.
     */
    public final int getPeekHeight() {
        return mPeekHeight;
    }

    /**
     * Gets the offset from the panel till the top
     *
     * @return the offset in pixel size
     */
    public final int getPanelOffset() {
        if (mState == STATE_EXPANDED) {
            return mMinOffset;
        } else if (mState == STATE_ANCHOR) {
            return mAnchorOffset;
        } else if (mHideable && mState == STATE_HIDDEN) {
            return mParentHeight;
        }
        return mMaxOffset;
    }

    public int getMinOffset() {
        return mMinOffset;
    }

    public void setMinOffset(int mMinOffset) {
        this.mMinOffset = mMinOffset;
    }

    /**
     * Get the size in pixels from the anchor state to the top of the parent (Expanded state)
     *
     * @return pixel size of the anchor state
     */
    public int getAnchorOffset() {
        return mAnchorOffset;
    }

    /**
     * The multiplier between 0..1 to calculate the Anchor offset
     *
     * @return float between 0..1
     */
    public float getAnchorThreshold() {
        return mAnchorThreshold;
    }

    /**
     * Set the offset for the anchor state. Number between 0..1
     * i.e: Anchor the panel at 1/3 of the screen: setAnchorOffset(0.25)
     *
     * @param threshold {@link Float} from 0..1
     */
    public void setAnchorOffset(float threshold) {
        this.mAnchorThreshold = threshold;
        this.mAnchorOffset = (int) Math.max(mParentHeight * mAnchorThreshold, mMinOffset);
    }

    /**
     * Sets whether this bottom sheet can hide when it is swiped down.
     *
     * @param hideable {@code true} to make this bottom sheet hideable.
     */
    public void setHideable(boolean hideable) {
        mHideable = hideable;
    }

    /**
     * Gets whether this bottom sheet can hide when it is swiped down.
     *
     * @return {@code true} if this bottom sheet can hide.
     */
    public boolean isHideable() {
        return mHideable;
    }

    /**
     * Sets whether this bottom sheet should skip the collapsed state when it is being hidden
     * after it is expanded once. Setting this to true has no effect unless the sheet is hideable.
     *
     * @param skipCollapsed True if the bottom sheet should skip the collapsed state.
     */
    public void setSkipCollapsed(boolean skipCollapsed) {
        mSkipCollapsed = skipCollapsed;
    }

    /**
     * Sets whether this bottom sheet should skip the collapsed state when it is being hidden
     * after it is expanded once.
     *
     * @return Whether the bottom sheet should skip the collapsed state.
     */
    public boolean getSkipCollapsed() {
        return mSkipCollapsed;
    }

    /**
     * Sets a callback to be notified of bottom sheet events.
     *
     * @param callback The callback to notify when bottom sheet events occur.
     */
    public void setAnchorSheetCallback(AnchorSheetCallback callback) {
        mCallback = callback;
    }

    /**
     * @return Current State of the Sheet
     */
    @State
    public final int getState() {
        return mState;
    }

    /**
     * Provides callback
     *
     * @param state State of the Sheet
     */
    private void setStateInternal(@State int state) {
        if (mState == state) {
            return;
        }
        mState = state;
        Component bottomSheet = mViewRef.get();
        if (bottomSheet != null && mCallback != null) {
            mCallback.onStateChanged(bottomSheet, state);
        }
    }

    /**
     * resets the velocityTracker
     */
    private void reset() {
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
            mVelocityTracker = null;
        }
    }

    /**
     * Checks whether to hide the sheet or not pending upon the component
     * position and velocity at which it is thrown
     *
     * @param child Captured component
     * @param yvel Y velocity
     * @return Whether to hide the sheet or not
     */
    boolean shouldHide(Component child, float yvel) {
        if (mSkipCollapsed) {
            return true;
        }
        if (child.getContentPositionY() < mMaxOffset) {
            // It should not hide, but collapse.
            return false;
        }
        final float newTop = child.getContentPositionY() + yvel * HIDE_FRICTION;
        return Math.abs(newTop - mMaxOffset) / (float) mPeekHeight > HIDE_THRESHOLD;
    }

    void dispatchOnSlide(int top) {
        Component bottomSheet = mViewRef.get();
        if (bottomSheet != null && mCallback != null) {
            if (top > mMaxOffset) {
                mCallback.onSlide(bottomSheet, (float) (mMaxOffset - top) /
                        (mParentHeight - mMaxOffset));
            } else {
                mCallback.onSlide(bottomSheet,
                        (float) (mMaxOffset - top) / ((mMaxOffset - mMinOffset)));
            }
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
            y = touchEvent.getPointerPosition(index).getY();
        }
        return y;
    }

    /**
     * Sets the state of the bottom sheet. The bottom sheet will transition to that state with
     * animation.
     *
     * @param state One of {@link #STATE_COLLAPSED}, {@link #STATE_EXPANDED}, or
     *              {@link #STATE_HIDDEN}.
     */
    public final void setState(@State int state) {
        if(mState == state){
            return;
        }
        if (mViewRef == null) {
            // The view is not laid out yet; modify mState and let onLayoutChild handle it later
            if (state == STATE_COLLAPSED || state == STATE_EXPANDED || state == STATE_ANCHOR ||
                    ((mHideable && state == STATE_HIDDEN) || state == STATE_FORCE_HIDDEN)) {
                mState = state;
            }
            return;
        }
        ComponentContainer child = mViewRef.get();
        if (child == null) {
            return;
        }
        startSettlingAnimation(child,state,0);
    }


    void startSettlingAnimation(Component child, int state, int yvel) {
        int top;
        int currentTop = (int) child.getContentPositionY();
        if (state == STATE_ANCHOR) {
            top = mAnchorOffset;
        } else if (state == STATE_COLLAPSED) {
            top = mMaxOffset;
        } else if (state == STATE_EXPANDED) {
            top = mMinOffset;
        } else if ((mHideable && state == STATE_HIDDEN) || state == STATE_FORCE_HIDDEN) {
            top = mParentHeight;
        } else {
            throw new IllegalArgumentException("Illegal state argument: " + state);
        }
        AnimatorValue animatorValue = new AnimatorValue();
        animatorValue.setDuration(computeSettleDuration(child,0,currentTop-top,0,yvel));
        animatorValue.setLoopedCount(0);
        animatorValue.setCurveType(Animator.CurveType.LINEAR);
        animatorValue.setValueUpdateListener((animatorValue1, v) -> {
            child.setContentPositionY(v*(top-currentTop)+currentTop);
            if(v > 0.999999f){
                setStateInternal(state);
            }
            else{
                setStateInternal(STATE_SETTLING);
            }
        });
        animatorValue.start();
    }

    /**
     * Called by {@link #startSettlingAnimation(Component, int, int) } to find
     * the duration of the animation
     *
     * @param child component on which animation is going
     * @param dx X distance
     * @param dy Y distance
     * @param xvel X Velocity
     * @param yvel Y Velocity
     * @return time in milliseconds
     */
    private int computeSettleDuration(Component child, int dx, int dy, int xvel, int yvel) {
        xvel = this.clampMag(xvel, (int)mDragHelper.getMinVelocity(), (int)mDragHelper.getMaxVelocity());
        yvel = this.clampMag(yvel, (int)mDragHelper.getMinVelocity(), (int)mDragHelper.getMaxVelocity());
        int absDx = Math.abs(dx);
        int absDy = Math.abs(dy);
        int absXVel = Math.abs(xvel);
        int absYVel = Math.abs(yvel);
        int addedVel = absXVel + absYVel;
        int addedDistance = absDx + absDy;
        float xweight = xvel != 0 ? (float)absXVel / (float)addedVel : (float)absDx / (float)addedDistance;
        float yweight = yvel != 0 ? (float)absYVel / (float)addedVel : (float)absDy / (float)addedDistance;
        int xduration = this.computeAxisDuration(dx, xvel, mDragHelper.getCallback().getViewHorizontalDragRange(child));
        int yduration = this.computeAxisDuration(dy, yvel, mDragHelper.getCallback().getViewVerticalDragRange(child));
        return (int)((float)xduration * xweight + (float)yduration * yweight);
    }

    private int computeAxisDuration(int delta, int velocity, int motionRange) {
        if (delta == 0) {
            return 0;
        } else {
            int width = this.getWidth();
            int halfWidth = width / 2;
            float distanceRatio = Math.min(1.0F, (float)Math.abs(delta) / (float)width);
            float distance = (float)halfWidth + (float)halfWidth * this.distanceInfluenceForSnapDuration(distanceRatio);
            velocity = Math.abs(velocity);
            int duration;
            if (velocity > 0) {
                duration = 4 * Math.round(1000.0F * Math.abs(distance / (float)velocity));
            } else {
                float range = (float)Math.abs(delta) / (float)motionRange;
                duration = (int)((range + 1.0F) * 256.0F);
            }
            return Math.min(duration, 200);
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
    private int clampMag(int value, int absMin, int absMax) {
        int absValue = Math.abs(value);
        if (absValue < absMin) {
            return 0;
        } else if (absValue > absMax) {
            return value > 0 ? absMax : -absMax;
        } else {
            return value;
        }
    }

    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5F; // center the values about 0.
        f *= 0.47123894F;
        return (float)Math.sin(f);
    }

}
