package com.getbase.floatingactionbutton;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

public class FloatingActionsMenu extends ViewGroup {
    public static final int EXPAND_UP = 0;
    public static final int EXPAND_DOWN = 1;
    public static final int EXPAND_LEFT = 2;
    public static final int EXPAND_RIGHT = 3;

    public static final int LABELS_ON_LEFT_SIDE = 0;
    public static final int LABELS_ON_RIGHT_SIDE = 1;

    private static final float COLLAPSED_PLUS_ROTATION = 0f;
    private static final float EXPANDED_PLUS_ROTATION = 90f + 45f;

    public long mAnimationFadeOutDuration;
    public long mAnimationItemDuration;
    public long mOneItemDelay;
    public long mOneItemLabelDelay;
    public long mAnimationLabelDuration;

    private int mAddButtonPlusColor;
    private int mAddButtonColorNormal;
    private int mAddButtonColorPressed;
    private int mAddButtonSize;
    private boolean mAddButtonStrokeVisible;
    private int mExpandDirection;

    private int mButtonSpacing;
    private int mAnimationTranslation;
    private int mAnimationLabelTranslation;
    private int mLabelsMargin;
    private int mLabelsVerticalOffset;

    private boolean mExpanded;

    private AnimatorSet mExpandAnimation = new AnimatorSet();
    private AnimatorSet mCollapseAnimation = new AnimatorSet();
    private boolean mExpandAnimationInProgress;
    private boolean mCollapseAnimationInProgress;
    private FloatingActionButton mAddButton;
    private RotatingDrawable mRotatingDrawable;
    private int mMaxButtonWidth;
    private int mMaxButtonHeight;
    private int mLabelsStyle;
    private int mLabelsPosition;
    private int mButtonsCount;

    private TouchDelegateGroup mTouchDelegateGroup;

    private OnFloatingActionsMenuUpdateListener mListener;

    public interface OnFloatingActionsMenuUpdateListener {
        void onMenuExpandStarted();
        void onMenuExpandEnded();
        void onMenuCollapseStarted();
        void onMenuCollapseEnded();
    }

    public FloatingActionsMenu(Context context) {
        this(context, null);
    }

    public FloatingActionsMenu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init(context, attrs);
    }

    public FloatingActionsMenu(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attributeSet) {
        mAnimationFadeOutDuration = context.getResources().getInteger(R.integer.fab_animation_fade_out_duration);
        mAnimationItemDuration = context.getResources().getInteger(R.integer.fab_animation_item_duration);
        mOneItemDelay = context.getResources().getInteger(R.integer.fab_animation_item_delay);
        mOneItemLabelDelay = context.getResources().getInteger(R.integer.fab_animation_label_delay);
        mAnimationLabelDuration = context.getResources().getInteger(R.integer.fab_animation_label_duration);

        mAnimationTranslation = getResources().getDimensionPixelSize(R.dimen.fab_animation_translation);
        mAnimationLabelTranslation = getResources().getDimensionPixelSize(R.dimen.fab_animation_label_translation);
        mButtonSpacing = (int) (getResources().getDimension(R.dimen.fab_actions_spacing) - getResources().getDimension(R.dimen.fab_shadow_radius) - getResources().getDimension(R.dimen.fab_shadow_offset));
        mLabelsMargin = getResources().getDimensionPixelSize(R.dimen.fab_labels_margin);
        mLabelsVerticalOffset = getResources().getDimensionPixelSize(R.dimen.fab_shadow_offset);

        mTouchDelegateGroup = new TouchDelegateGroup(this);
        setTouchDelegate(mTouchDelegateGroup);

        TypedArray attr = context.obtainStyledAttributes(attributeSet, R.styleable.FloatingActionsMenu, 0, 0);
        mAddButtonPlusColor = attr.getColor(R.styleable.FloatingActionsMenu_fab_addButtonPlusIconColor, getColor(android.R.color.white));
        mAddButtonColorNormal = attr.getColor(R.styleable.FloatingActionsMenu_fab_addButtonColorNormal, getColor(android.R.color.holo_blue_dark));
        mAddButtonColorPressed = attr.getColor(R.styleable.FloatingActionsMenu_fab_addButtonColorPressed, getColor(android.R.color.holo_blue_light));
        mAddButtonSize = attr.getInt(R.styleable.FloatingActionsMenu_fab_addButtonSize, FloatingActionButton.SIZE_NORMAL);
        mAddButtonStrokeVisible = attr.getBoolean(R.styleable.FloatingActionsMenu_fab_addButtonStrokeVisible, true);
        mExpandDirection = attr.getInt(R.styleable.FloatingActionsMenu_fab_expandDirection, EXPAND_UP);
        mLabelsStyle = attr.getResourceId(R.styleable.FloatingActionsMenu_fab_labelStyle, 0);
        mLabelsPosition = attr.getInt(R.styleable.FloatingActionsMenu_fab_labelsPosition, LABELS_ON_LEFT_SIDE);
        attr.recycle();

        if (mLabelsStyle != 0 && expandsHorizontally()) {
            throw new IllegalStateException("Action labels in horizontal expand orientation is not supported.");
        }

        setMainButton(createAddButton(context));
    }

    public void setOnFloatingActionsMenuUpdateListener(OnFloatingActionsMenuUpdateListener listener) {
        mListener = listener;
    }

    private boolean expandsHorizontally() {
        return mExpandDirection == EXPAND_LEFT || mExpandDirection == EXPAND_RIGHT;
    }

    private static class RotatingDrawable extends LayerDrawable {
        public RotatingDrawable(Drawable drawable) {
            super(new Drawable[]{drawable});
        }

        private float mRotation;

        @SuppressWarnings("UnusedDeclaration")
        public float getRotation() {
            return mRotation;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setRotation(float rotation) {
            mRotation = rotation;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.save();
            canvas.rotate(mRotation, getBounds().centerX(), getBounds().centerY());
            super.draw(canvas);
            canvas.restore();
        }
    }

    public void setMainButton(FloatingActionButton button) {
        if (mAddButton != null) {
            removeView(mAddButton.getLabelView());
            removeView(mAddButton);
        }
        mAddButton = button;
        mAddButton.setId(R.id.fab_expand_menu_button);
        mAddButton.setSize(mAddButtonSize);
        mAddButton.setOnClickListener(getMainButtonClickListener());

        addView(mAddButton, super.generateDefaultLayoutParams());
    }

    public OnClickListener getMainButtonClickListener() {
        return new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle();
            }
        };
    }

    private AddFloatingActionButton createAddButton(Context context) {
        return new AddFloatingActionButton(context) {
            @Override
            void updateBackground() {
                mPlusColor = mAddButtonPlusColor;
                mColorNormal = mAddButtonColorNormal;
                mColorPressed = mAddButtonColorPressed;
                mStrokeVisible = mAddButtonStrokeVisible;
                super.updateBackground();
            }

            @Override
            Drawable getIconDrawable() {
                final RotatingDrawable rotatingDrawable = new RotatingDrawable(super.getIconDrawable());
                mRotatingDrawable = rotatingDrawable;

                final OvershootInterpolator interpolator = new OvershootInterpolator();

                final ObjectAnimator collapseAnimator = ObjectAnimator.ofFloat(rotatingDrawable, "rotation", EXPANDED_PLUS_ROTATION, COLLAPSED_PLUS_ROTATION);
                final ObjectAnimator expandAnimator = ObjectAnimator.ofFloat(rotatingDrawable, "rotation", COLLAPSED_PLUS_ROTATION, EXPANDED_PLUS_ROTATION);

                collapseAnimator.setInterpolator(interpolator);
                expandAnimator.setInterpolator(interpolator);

                mExpandAnimation.play(expandAnimator);
                mCollapseAnimation.play(collapseAnimator);

                return rotatingDrawable;
            }
        };
    }

    public void addButton(FloatingActionButton button) {
        addView(button, mButtonsCount - 1);
        mButtonsCount++;

        if (mLabelsStyle != 0) {
            createLabels();
        }
    }

    public void removeButton(FloatingActionButton button) {
        removeView(button.getLabelView());
        removeView(button);
        mButtonsCount--;
    }

    private int getColor(@ColorRes int id) {
        return getResources().getColor(id);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);

        int width = 0;
        int height = 0;

        mMaxButtonWidth = 0;
        mMaxButtonHeight = 0;
        int maxLabelWidth = 0;

        for (int i = 0; i < mButtonsCount; i++) {
            View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            switch (mExpandDirection) {
                case EXPAND_UP:
                case EXPAND_DOWN:
                    mMaxButtonWidth = Math.max(mMaxButtonWidth, child.getMeasuredWidth());
                    height += child.getMeasuredHeight();
                    break;
                case EXPAND_LEFT:
                case EXPAND_RIGHT:
                    width += child.getMeasuredWidth();
                    mMaxButtonHeight = Math.max(mMaxButtonHeight, child.getMeasuredHeight());
                    break;
            }

            if (!expandsHorizontally()) {
                TextView label = (TextView) child.getTag(R.id.fab_label);
                if (label != null) {
                    maxLabelWidth = Math.max(maxLabelWidth, label.getMeasuredWidth());
                }
            }
        }

        if (!expandsHorizontally()) {
            width = mMaxButtonWidth + (maxLabelWidth > 0 ? maxLabelWidth + mLabelsMargin : 0);
        } else {
            height = mMaxButtonHeight;
        }

        switch (mExpandDirection) {
            case EXPAND_UP:
            case EXPAND_DOWN:
                height += mButtonSpacing * (getChildCount() - 1);
                height = adjustForOvershoot(height);
                break;
            case EXPAND_LEFT:
            case EXPAND_RIGHT:
                width += mButtonSpacing * (getChildCount() - 1);
                width = adjustForOvershoot(width);
                break;
        }

        setMeasuredDimension(width, height);
    }

    private int adjustForOvershoot(int dimension) {
        return dimension * 12 / 10;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mTouchDelegateGroup.removeAllTouchDelegates();
        switch (mExpandDirection) {
            case EXPAND_UP:
            case EXPAND_DOWN:
                boolean expandUp = mExpandDirection == EXPAND_UP;

                if (changed) {
                    mTouchDelegateGroup.clearTouchDelegates();
                }

                int addButtonY = expandUp ? b - t - mAddButton.getMeasuredHeight() : 0;
                // Ensure mAddButton is centered on the line where the buttons should be
                int buttonsHorizontalCenter = mLabelsPosition == LABELS_ON_LEFT_SIDE
                        ? r - l - mMaxButtonWidth / 2
                        : mMaxButtonWidth / 2;
                int addButtonLeft = buttonsHorizontalCenter - mAddButton.getMeasuredWidth() / 2;
                mAddButton.layout(addButtonLeft, addButtonY, addButtonLeft + mAddButton.getMeasuredWidth(), addButtonY + mAddButton.getMeasuredHeight());

                float expandedTranslation = 0f;

                int labelsOffset = mMaxButtonWidth / 2 + mLabelsMargin;
                int labelsXNearButton = mLabelsPosition == LABELS_ON_LEFT_SIDE
                        ? buttonsHorizontalCenter - labelsOffset
                        : buttonsHorizontalCenter + labelsOffset;

                int nextY = expandUp ?
                        addButtonY - mButtonSpacing :
                        addButtonY + mAddButton.getMeasuredHeight() + mButtonSpacing;

                View label = (View) mAddButton.getTag(R.id.fab_label);
                if (label != null) {
                    layoutLabel(labelsXNearButton, mAddButton, addButtonLeft, addButtonY, expandedTranslation, 0, label, 0);
                }

                for (int i = mButtonsCount - 1; i >= 0; i--) {
                    final View child = getChildAt(i);

                    if (child == mAddButton || child.getVisibility() == GONE) continue;

                    int childX = buttonsHorizontalCenter - child.getMeasuredWidth() / 2;
                    int childY = expandUp ? nextY - child.getMeasuredHeight() : nextY;
                    child.layout(childX, childY, childX + child.getMeasuredWidth(), childY + child.getMeasuredHeight());

                    child.setTranslationY(mExpanded ? expandedTranslation : mAnimationTranslation);
                    child.setAlpha(mExpanded ? 1f : 0f);

                    LayoutParams params = (LayoutParams) child.getLayoutParams();
                    params.mExpandDir.setFloatValues(mAnimationTranslation, expandedTranslation);

                    int expandDelay = (int) (mOneItemDelay * (mButtonsCount - 2 - i));
                    params.setAnimationsTarget(child, expandDelay, mAnimationItemDuration);

                    label = (View) child.getTag(R.id.fab_label);
                    if (label != null) {
                        layoutLabel(labelsXNearButton, child, childX, childY, expandedTranslation, expandDelay, label, mAnimationLabelTranslation);
                    }

                    nextY = expandUp ?
                            childY - mButtonSpacing :
                            childY + child.getMeasuredHeight() + mButtonSpacing;
                }

                break;

            case EXPAND_LEFT:
            case EXPAND_RIGHT:
                boolean expandLeft = mExpandDirection == EXPAND_LEFT;

                int addButtonX = expandLeft ? r - l - mAddButton.getMeasuredWidth() : 0;
                // Ensure mAddButton is centered on the line where the buttons should be
                int addButtonTop = b - t - mMaxButtonHeight + (mMaxButtonHeight - mAddButton.getMeasuredHeight()) / 2;
                mAddButton.layout(addButtonX, addButtonTop, addButtonX + mAddButton.getMeasuredWidth(), addButtonTop + mAddButton.getMeasuredHeight());

                int nextX = expandLeft ?
                        addButtonX - mButtonSpacing :
                        addButtonX + mAddButton.getMeasuredWidth() + mButtonSpacing;

                for (int i = mButtonsCount - 1; i >= 0; i--) {
                    final View child = getChildAt(i);

                    if (child == mAddButton || child.getVisibility() == GONE) continue;

                    int childX = expandLeft ? nextX - child.getMeasuredWidth() : nextX;
                    int childY = addButtonTop + (mAddButton.getMeasuredHeight() - child.getMeasuredHeight()) / 2;
                    child.layout(childX, childY, childX + child.getMeasuredWidth(), childY + child.getMeasuredHeight());

                    float collapsedTranslation = addButtonX - childX;
                    expandedTranslation = 0f;

                    child.setTranslationX(mExpanded ? expandedTranslation : collapsedTranslation);
                    child.setAlpha(mExpanded ? 1f : 0f);

                    LayoutParams params = (LayoutParams) child.getLayoutParams();
                    params.mExpandDir.setFloatValues(collapsedTranslation, expandedTranslation);
                    params.setAnimationsTarget(child, 0, mAnimationItemDuration);

                    nextX = expandLeft ?
                            childX - mButtonSpacing :
                            childX + child.getMeasuredWidth() + mButtonSpacing;
                }

                break;
        }
    }

    private void layoutLabel(int labelsXNearButton, View child, int childX, int childY, float expandedTranslation,
                             int expandDelay, View label, int animationTranslation) {
        int labelXAwayFromButton = mLabelsPosition == LABELS_ON_LEFT_SIDE
                ? labelsXNearButton - label.getMeasuredWidth()
                : labelsXNearButton + label.getMeasuredWidth();

        int labelLeft = mLabelsPosition == LABELS_ON_LEFT_SIDE
                ? labelXAwayFromButton
                : labelsXNearButton;

        int labelRight = mLabelsPosition == LABELS_ON_LEFT_SIDE
                ? labelsXNearButton
                : labelXAwayFromButton;

        int labelTop = childY - mLabelsVerticalOffset + (child.getMeasuredHeight() - label.getMeasuredHeight()) / 2;

        label.layout(labelLeft, labelTop, labelRight, labelTop + label.getMeasuredHeight());

        Rect touchArea = new Rect(
                Math.min(childX, labelLeft),
                childY - mButtonSpacing / 2,
                Math.max(childX + child.getMeasuredWidth(), labelRight),
                childY + child.getMeasuredHeight() + mButtonSpacing / 2);
        mTouchDelegateGroup.addTouchDelegate(child, new TouchDelegate(touchArea, child));

        label.setTranslationY(mExpanded ? expandedTranslation : mAnimationTranslation);
        label.setAlpha(mExpanded ? 1f : 0f);

        LayoutParams labelParams = (LayoutParams) label.getLayoutParams();
        labelParams.mExpandDir.setFloatValues(animationTranslation, expandedTranslation);
        labelParams.mExpandScaleX.setFloatValues(LayoutParams.SCALE_EXPAND, LayoutParams.SCALE_EXPAND);
        labelParams.mExpandScaleY.setFloatValues(LayoutParams.SCALE_EXPAND, LayoutParams.SCALE_EXPAND);
        labelParams.setAnimationsTarget(label, (int) (expandDelay + mOneItemLabelDelay), mAnimationLabelDuration);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(super.generateDefaultLayoutParams());
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(super.generateLayoutParams(attrs));
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(super.generateLayoutParams(p));
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return super.checkLayoutParams(p);
    }

    private static Interpolator sExpandInterpolator = new DecelerateInterpolator();
    private static Interpolator sAlphaExpandInterpolator = new DecelerateInterpolator();
    private static Interpolator sCollapseInterpolator = new DecelerateInterpolator(3f);

    private class LayoutParams extends ViewGroup.LayoutParams {
        private final static float ALPHA_COLLAPSE = 0f;
        private final static float ALPHA_EXPAND = 1f;
        private final static float SCALE_COLLAPSE = 0.4f;
        private final static float SCALE_EXPAND = 1f;

        private ObjectAnimator mExpandDir = new ObjectAnimator();
        private ObjectAnimator mExpandAlpha = new ObjectAnimator();
        private ObjectAnimator mExpandScaleX = new ObjectAnimator();
        private ObjectAnimator mExpandScaleY = new ObjectAnimator();

        private ObjectAnimator mCollapseAlpha = new ObjectAnimator();
        private boolean animationsSetToPlay;

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            mCollapseAlpha.setDuration(mAnimationFadeOutDuration);

            mExpandDir.setInterpolator(sExpandInterpolator);
            mExpandAlpha.setInterpolator(sAlphaExpandInterpolator);
            mExpandScaleX.setInterpolator(sAlphaExpandInterpolator);
            mExpandScaleY.setInterpolator(sAlphaExpandInterpolator);
            mCollapseAlpha.setInterpolator(sCollapseInterpolator);

            mCollapseAlpha.setProperty(View.ALPHA);
            mCollapseAlpha.setFloatValues(ALPHA_EXPAND, ALPHA_COLLAPSE);
            mExpandAlpha.setProperty(View.ALPHA);
            mExpandAlpha.setFloatValues(ALPHA_COLLAPSE, ALPHA_EXPAND);

            mExpandScaleX.setProperty(View.SCALE_X);
            mExpandScaleX.setFloatValues(SCALE_COLLAPSE, SCALE_EXPAND);

            mExpandScaleY.setProperty(View.SCALE_Y);
            mExpandScaleY.setFloatValues(SCALE_COLLAPSE, SCALE_EXPAND);

            switch (mExpandDirection) {
                case EXPAND_UP:
                case EXPAND_DOWN:
                    mExpandDir.setProperty(View.TRANSLATION_Y);
                    break;
                case EXPAND_LEFT:
                case EXPAND_RIGHT:
                    mExpandDir.setProperty(View.TRANSLATION_X);
                    break;
            }
        }

        public void setAnimationsTarget(View view, int expandDelay, long duration) {
            mExpandAlpha.setTarget(view);
            mExpandDir.setTarget(view);
            mExpandScaleX.setTarget(view);
            mExpandScaleY.setTarget(view);
            mCollapseAlpha.setTarget(view);

            mExpandDir.setDuration(duration);
            mExpandAlpha.setDuration(duration);
            mExpandScaleX.setDuration(duration);
            mExpandScaleY.setDuration(duration);

            mExpandAlpha.setStartDelay(expandDelay);
            mExpandDir.setStartDelay(expandDelay);
            mExpandScaleX.setStartDelay(expandDelay);
            mExpandScaleY.setStartDelay(expandDelay);


            // Now that the animations have targets, set them to be played
            if (!animationsSetToPlay) {
                mCollapseAnimation.play(mCollapseAlpha);

                mExpandAnimation.play(mExpandAlpha);
                mExpandAnimation.play(mExpandDir);
                mExpandAnimation.play(mExpandScaleX);
                mExpandAnimation.play(mExpandScaleY);
                animationsSetToPlay = true;
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        bringChildToFront(mAddButton);
        mButtonsCount = getChildCount();

        if (mLabelsStyle != 0) {
            createLabels();
        }
    }

    private void createLabels() {
        Context context = new ContextThemeWrapper(getContext(), mLabelsStyle);
        for (int i = 0; i < mButtonsCount; i++) {
            FloatingActionButton button = (FloatingActionButton) getChildAt(i);
            String title = button.getTitle();
            if (title != null && button.getTag(R.id.fab_label) == null) {
                TextView label = new TextView(context);
                label.setTextAppearance(getContext(), mLabelsStyle);
                label.setText(button.getTitle());
                addView(label);
                button.setTag(R.id.fab_label, label);
            }
        }
    }

    public void collapse() {
        if (mExpanded) {
            mExpanded = false;
            mTouchDelegateGroup.setEnabled(false);
            mCollapseAnimation.start();
            mCollapseAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mCollapseAnimationInProgress = false;
                    if (mListener != null) {
                        mListener.onMenuCollapseEnded();
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mCollapseAnimationInProgress = false;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                    mCollapseAnimationInProgress = true;
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    mCollapseAnimationInProgress = true;
                }
            });
            mExpandAnimation.cancel();

            if (mListener != null) {
                mListener.onMenuCollapseStarted();
            }
        }
    }

    public void toggle() {
        if (mExpanded) {
            collapse();
        } else {
            expand();
        }
    }

    public void expand() {
        if (!mExpanded) {
            mExpanded = true;
            mTouchDelegateGroup.setEnabled(true);
            mExpandAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mExpandAnimationInProgress = false;
                    if (mListener != null) {
                        mListener.onMenuExpandEnded();
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    mExpandAnimationInProgress = false;
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                    mExpandAnimationInProgress = true;
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    mExpandAnimationInProgress = true;
                }
            });
            mExpandAnimation.start();
            mCollapseAnimation.cancel();

            if (mListener != null) {
                mListener.onMenuExpandStarted();
            }
        }
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.mExpanded = mExpanded;

        return savedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            SavedState savedState = (SavedState) state;
            mExpanded = savedState.mExpanded;
            mTouchDelegateGroup.setEnabled(mExpanded);

            if (mRotatingDrawable != null) {
                mRotatingDrawable.setRotation(mExpanded ? EXPANDED_PLUS_ROTATION : COLLAPSED_PLUS_ROTATION);
            }

            super.onRestoreInstanceState(savedState.getSuperState());
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    public boolean isAnimationInProgress() {
        return mExpandAnimationInProgress || mCollapseAnimationInProgress;
    }

    public static class SavedState extends BaseSavedState {
        public boolean mExpanded;

        public SavedState(Parcelable parcel) {
            super(parcel);
        }

        private SavedState(Parcel in) {
            super(in);
            mExpanded = in.readInt() == 1;
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mExpanded ? 1 : 0);
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
