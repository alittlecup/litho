/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
import static android.os.Build.VERSION_CODES.JELLY_BEAN;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static com.facebook.litho.CommonUtils.addOrCreateList;
import static com.facebook.litho.ComponentContext.NULL_LAYOUT;
import static com.facebook.litho.ComponentsLogger.LogLevel.WARNING;
import static com.facebook.yoga.YogaEdge.ALL;
import static com.facebook.yoga.YogaEdge.BOTTOM;
import static com.facebook.yoga.YogaEdge.END;
import static com.facebook.yoga.YogaEdge.HORIZONTAL;
import static com.facebook.yoga.YogaEdge.LEFT;
import static com.facebook.yoga.YogaEdge.RIGHT;
import static com.facebook.yoga.YogaEdge.START;
import static com.facebook.yoga.YogaEdge.TOP;
import static com.facebook.yoga.YogaEdge.VERTICAL;

import android.animation.StateListAnimator;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PathEffect;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Px;
import androidx.core.view.ViewCompat;
import com.facebook.infer.annotation.OkToExtend;
import com.facebook.infer.annotation.ReturnsOwnership;
import com.facebook.infer.annotation.ThreadConfined;
import com.facebook.litho.config.ComponentsConfiguration;
import com.facebook.litho.drawable.ComparableColorDrawable;
import com.facebook.litho.drawable.ComparableDrawable;
import com.facebook.litho.drawable.ComparableResDrawable;
import com.facebook.litho.drawable.DefaultComparableDrawable;
import com.facebook.yoga.YogaAlign;
import com.facebook.yoga.YogaBaselineFunction;
import com.facebook.yoga.YogaConstants;
import com.facebook.yoga.YogaDirection;
import com.facebook.yoga.YogaEdge;
import com.facebook.yoga.YogaFlexDirection;
import com.facebook.yoga.YogaJustify;
import com.facebook.yoga.YogaMeasureFunction;
import com.facebook.yoga.YogaNode;
import com.facebook.yoga.YogaPositionType;
import com.facebook.yoga.YogaWrap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Default implementation of {@link InternalNode}. */
@OkToExtend
@ThreadConfined(ThreadConfined.ANY)
public class DefaultInternalNode implements InternalNode {

  // Used to check whether or not the framework can use style IDs for
  // paddingStart/paddingEnd due to a bug in some Android devices.
  private static final boolean SUPPORTS_RTL = (SDK_INT >= JELLY_BEAN_MR1);

  // Flags used to indicate that a certain attribute was explicitly set on the node.
  private static final long PFLAG_LAYOUT_DIRECTION_IS_SET = 1L << 0;
  private static final long PFLAG_ALIGN_SELF_IS_SET = 1L << 1;
  private static final long PFLAG_POSITION_TYPE_IS_SET = 1L << 2;
  private static final long PFLAG_FLEX_IS_SET = 1L << 3;
  private static final long PFLAG_FLEX_GROW_IS_SET = 1L << 4;
  private static final long PFLAG_FLEX_SHRINK_IS_SET = 1L << 5;
  private static final long PFLAG_FLEX_BASIS_IS_SET = 1L << 6;
  private static final long PFLAG_IMPORTANT_FOR_ACCESSIBILITY_IS_SET = 1L << 7;
  private static final long PFLAG_DUPLICATE_PARENT_STATE_IS_SET = 1L << 8;
  private static final long PFLAG_MARGIN_IS_SET = 1L << 9;
  private static final long PFLAG_PADDING_IS_SET = 1L << 10;
  private static final long PFLAG_POSITION_IS_SET = 1L << 11;
  private static final long PFLAG_WIDTH_IS_SET = 1L << 12;
  private static final long PFLAG_MIN_WIDTH_IS_SET = 1L << 13;
  private static final long PFLAG_MAX_WIDTH_IS_SET = 1L << 14;
  private static final long PFLAG_HEIGHT_IS_SET = 1L << 15;
  private static final long PFLAG_MIN_HEIGHT_IS_SET = 1L << 16;
  private static final long PFLAG_MAX_HEIGHT_IS_SET = 1L << 17;
  private static final long PFLAG_BACKGROUND_IS_SET = 1L << 18;
  private static final long PFLAG_FOREGROUND_IS_SET = 1L << 19;
  private static final long PFLAG_VISIBLE_HANDLER_IS_SET = 1L << 20;
  private static final long PFLAG_FOCUSED_HANDLER_IS_SET = 1L << 21;
  private static final long PFLAG_FULL_IMPRESSION_HANDLER_IS_SET = 1L << 22;
  private static final long PFLAG_INVISIBLE_HANDLER_IS_SET = 1L << 23;
  private static final long PFLAG_UNFOCUSED_HANDLER_IS_SET = 1L << 24;
  private static final long PFLAG_TOUCH_EXPANSION_IS_SET = 1L << 25;
  private static final long PFLAG_ASPECT_RATIO_IS_SET = 1L << 26;
  private static final long PFLAG_TRANSITION_KEY_IS_SET = 1L << 27;
  private static final long PFLAG_BORDER_IS_SET = 1L << 28;
  private static final long PFLAG_STATE_LIST_ANIMATOR_SET = 1L << 29;
  private static final long PFLAG_STATE_LIST_ANIMATOR_RES_SET = 1L << 30;
  private static final long PFLAG_VISIBLE_RECT_CHANGED_HANDLER_IS_SET = 1L << 31;
  private static final long PFLAG_TRANSITION_KEY_TYPE_IS_SET = 1L << 32;

  private final YogaNode mYogaNode;
  private final ComponentContext mComponentContext;

  @ThreadConfined(ThreadConfined.ANY)
  private final List<Component> mComponents = new ArrayList<>(1);

  private final int[] mBorderColors = new int[Border.EDGE_COUNT];
  private final float[] mBorderRadius = new float[Border.RADIUS_COUNT];

  private @Nullable DiffNode mDiffNode;
  private @Nullable NodeInfo mNodeInfo;
  private @Nullable NestedTreeProps mNestedTreeProps;
  private @Nullable EventHandler<VisibleEvent> mVisibleHandler;
  private @Nullable EventHandler<FocusedVisibleEvent> mFocusedHandler;
  private @Nullable EventHandler<UnfocusedVisibleEvent> mUnfocusedHandler;
  private @Nullable EventHandler<FullImpressionVisibleEvent> mFullImpressionHandler;
  private @Nullable EventHandler<InvisibleEvent> mInvisibleHandler;
  private @Nullable EventHandler<VisibilityChangedEvent> mVisibilityChangedHandler;
  private @Nullable ComparableDrawable mBackground;
  private @Nullable ComparableDrawable mForeground;
  private @Nullable PathEffect mBorderPathEffect;
  private @Nullable StateListAnimator mStateListAnimator;
  private @Nullable boolean[] mIsPaddingPercent;
  private @Nullable Edges mTouchExpansion;
  private @Nullable String mTransitionKey;
  private @Nullable Transition.TransitionKeyType mTransitionKeyType;
  private @Nullable ArrayList<Transition> mTransitions;
  private @Nullable ArrayList<Component> mComponentsNeedingPreviousRenderData;
  private @Nullable ArrayList<WorkingRangeContainer.Registration> mWorkingRangeRegistrations;
  private @Nullable String mTestKey;
  private @Nullable Set<DebugComponent> mDebugComponents = null;

  private boolean mDuplicateParentState;
  private boolean mForceViewWrapping;
  private boolean mCachedMeasuresValid;

  private int mImportantForAccessibility = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
  private @DrawableRes int mStateListAnimatorRes;

  private float mVisibleHeightRatio;
  private float mVisibleWidthRatio;
  private float mResolvedTouchExpansionLeft = YogaConstants.UNDEFINED;
  private float mResolvedTouchExpansionRight = YogaConstants.UNDEFINED;
  private float mResolvedX = YogaConstants.UNDEFINED;
  private float mResolvedY = YogaConstants.UNDEFINED;
  private float mResolvedWidth = YogaConstants.UNDEFINED;
  private float mResolvedHeight = YogaConstants.UNDEFINED;

  private int mLastWidthSpec = DiffNode.UNSPECIFIED;
  private int mLastHeightSpec = DiffNode.UNSPECIFIED;
  private float mLastMeasuredWidth = DiffNode.UNSPECIFIED;
  private float mLastMeasuredHeight = DiffNode.UNSPECIFIED;

  private long mPrivateFlags;

  protected DefaultInternalNode(ComponentContext componentContext) {
    this(componentContext, createYogaNode(componentContext), true);
  }

  protected DefaultInternalNode(ComponentContext componentContext, YogaNode yogaNode) {
    this(componentContext, yogaNode, true);
  }

  protected DefaultInternalNode(
      ComponentContext componentContext, boolean createDebugComponentsInCtor) {
    this(componentContext, createYogaNode(componentContext));
  }

  private DefaultInternalNode(
      ComponentContext componentContext, YogaNode yogaNode, boolean createDebugComponentsInCtor) {
    if (createDebugComponentsInCtor) {
      mDebugComponents = new HashSet<>();
    }

    if (yogaNode != null) {
      yogaNode.setData(this);
    }
    mYogaNode = yogaNode;
    mComponentContext = componentContext;
  }

  @Override
  public void addChildAt(InternalNode child, int index) {
    mYogaNode.addChildAt(child.getYogaNode(), index);
  }

  @Override
  public void addComponentNeedingPreviousRenderData(Component component) {
    if (mComponentsNeedingPreviousRenderData == null) {
      mComponentsNeedingPreviousRenderData = new ArrayList<>(1);
    }
    mComponentsNeedingPreviousRenderData.add(component);
  }

  @Override
  public void addTransition(Transition transition) {
    if (mTransitions == null) {
      mTransitions = new ArrayList<>(1);
    }
    mTransitions.add(transition);
  }

  @Override
  public void addWorkingRanges(List<WorkingRangeContainer.Registration> registrations) {
    if (mWorkingRangeRegistrations == null) {
      mWorkingRangeRegistrations = new ArrayList<>(registrations.size());
    }
    mWorkingRangeRegistrations.addAll(registrations);
  }

  @Override
  public InternalNode alignContent(YogaAlign alignContent) {
    mYogaNode.setAlignContent(alignContent);
    return this;
  }

  @Override
  public InternalNode alignItems(YogaAlign alignItems) {
    mYogaNode.setAlignItems(alignItems);
    return this;
  }

  @Override
  public InternalNode alignSelf(YogaAlign alignSelf) {
    mPrivateFlags |= PFLAG_ALIGN_SELF_IS_SET;
    mYogaNode.setAlignSelf(alignSelf);
    return this;
  }

  @Override
  public void appendComponent(Component component) {
    mComponents.add(component);
  }

  @Override
  public boolean areCachedMeasuresValid() {
    return mCachedMeasuresValid;
  }

  @Override
  public InternalNode aspectRatio(float aspectRatio) {
    mPrivateFlags |= PFLAG_ASPECT_RATIO_IS_SET;
    mYogaNode.setAspectRatio(aspectRatio);
    return this;
  }

  @Override
  public InternalNode background(@Nullable ComparableDrawable background) {
    mPrivateFlags |= PFLAG_BACKGROUND_IS_SET;
    mBackground = background;
    setPaddingFromBackground(background);
    return this;
  }

  /**
   * @deprecated use {@link #background(ComparableDrawable)} more efficient diffing of drawables.
   */
  @Deprecated
  @Override
  public InternalNode background(@Nullable Drawable background) {
    if (background instanceof ComparableDrawable) {
      return background((ComparableDrawable) background);
    }
    return background(background != null ? DefaultComparableDrawable.create(background) : null);
  }

  @Override
  public InternalNode backgroundColor(@ColorInt int backgroundColor) {
    return background(ComparableColorDrawable.create(backgroundColor));
  }

  @Override
  public InternalNode backgroundRes(@DrawableRes int resId) {
    if (resId == 0) {
      return background((ComparableDrawable) null);
    }

    return background(ComparableResDrawable.create(mComponentContext.getAndroidContext(), resId));
  }

  @Override
  public InternalNode border(Border border) {
    mPrivateFlags |= PFLAG_BORDER_IS_SET;
    for (int i = 0, length = border.mEdgeWidths.length; i < length; ++i) {
      setBorderWidth(Border.edgeFromIndex(i), border.mEdgeWidths[i]);
    }
    System.arraycopy(border.mEdgeColors, 0, mBorderColors, 0, mBorderColors.length);
    System.arraycopy(border.mRadius, 0, mBorderRadius, 0, mBorderRadius.length);
    mBorderPathEffect = border.mPathEffect;
    return this;
  }

  @Override
  public void border(Edges width, int[] colors, float[] radii) {
    mPrivateFlags |= PFLAG_BORDER_IS_SET;

    mYogaNode.setBorder(LEFT, width.getRaw(YogaEdge.LEFT));
    mYogaNode.setBorder(TOP, width.getRaw(YogaEdge.TOP));
    mYogaNode.setBorder(RIGHT, width.getRaw(YogaEdge.RIGHT));
    mYogaNode.setBorder(BOTTOM, width.getRaw(YogaEdge.BOTTOM));
    mYogaNode.setBorder(VERTICAL, width.getRaw(YogaEdge.VERTICAL));
    mYogaNode.setBorder(HORIZONTAL, width.getRaw(YogaEdge.HORIZONTAL));
    mYogaNode.setBorder(START, width.getRaw(YogaEdge.START));
    mYogaNode.setBorder(END, width.getRaw(YogaEdge.END));
    mYogaNode.setBorder(ALL, width.getRaw(YogaEdge.ALL));

    System.arraycopy(colors, 0, mBorderColors, 0, colors.length);
    System.arraycopy(radii, 0, mBorderRadius, 0, radii.length);
  }

  @Override
  public void calculateLayout(float width, float height) {
    applyOverridesRecursive(this);
    mYogaNode.calculateLayout(width, height);
  }

  @Override
  public void calculateLayout() {
    calculateLayout(YogaConstants.UNDEFINED, YogaConstants.UNDEFINED);
  }

  @Override
  public InternalNode child(Component child) {
    if (child != null) {
      return child(Layout.create(mComponentContext, child));
    }

    return this;
  }

  @Override
  public InternalNode child(Component.Builder<?> child) {
    if (child != null) {
      child(child.build());
    }
    return this;
  }

  @Override
  public InternalNode child(InternalNode child) {
    if (child != null && child != NULL_LAYOUT) {
      addChildAt(child, mYogaNode.getChildCount());
    }

    return this;
  }

  @Override
  public InternalNode duplicateParentState(boolean duplicateParentState) {
    mPrivateFlags |= PFLAG_DUPLICATE_PARENT_STATE_IS_SET;
    mDuplicateParentState = duplicateParentState;
    return this;
  }

  @Override
  public InternalNode flex(float flex) {
    mPrivateFlags |= PFLAG_FLEX_IS_SET;
    mYogaNode.setFlex(flex);
    return this;
  }

  // Used by stetho to re-set auto value
  @Override
  public InternalNode flexBasisAuto() {
    mYogaNode.setFlexBasisAuto();
    return this;
  }

  @Override
  public InternalNode flexBasisPercent(float percent) {
    mPrivateFlags |= PFLAG_FLEX_BASIS_IS_SET;
    mYogaNode.setFlexBasisPercent(percent);
    return this;
  }

  @Override
  public InternalNode flexBasisPx(@Px int flexBasis) {
    mPrivateFlags |= PFLAG_FLEX_BASIS_IS_SET;
    mYogaNode.setFlexBasis(flexBasis);
    return this;
  }

  @Override
  public InternalNode flexDirection(YogaFlexDirection direction) {
    mYogaNode.setFlexDirection(direction);
    return this;
  }

  @Override
  public InternalNode flexGrow(float flexGrow) {
    mPrivateFlags |= PFLAG_FLEX_GROW_IS_SET;
    mYogaNode.setFlexGrow(flexGrow);
    return this;
  }

  @Override
  public InternalNode flexShrink(float flexShrink) {
    mPrivateFlags |= PFLAG_FLEX_SHRINK_IS_SET;
    mYogaNode.setFlexShrink(flexShrink);
    return this;
  }

  @Override
  public InternalNode focusedHandler(@Nullable EventHandler<FocusedVisibleEvent> focusedHandler) {
    mPrivateFlags |= PFLAG_FOCUSED_HANDLER_IS_SET;
    mFocusedHandler = addVisibilityHandler(mFocusedHandler, focusedHandler);
    return this;
  }

  /**
   * @deprecated use {@link #foreground(ComparableDrawable)} more efficient diffing of drawables.
   */
  @Deprecated
  @Override
  public InternalNode foreground(@Nullable Drawable foreground) {
    return foreground(foreground != null ? DefaultComparableDrawable.create(foreground) : null);
  }

  @Override
  public InternalNode foreground(@Nullable ComparableDrawable foreground) {
    mPrivateFlags |= PFLAG_FOREGROUND_IS_SET;
    mForeground = foreground;
    return this;
  }

  @Override
  public InternalNode foregroundColor(@ColorInt int foregroundColor) {
    return foreground(ComparableColorDrawable.create(foregroundColor));
  }

  @Override
  public InternalNode foregroundRes(@DrawableRes int resId) {
    if (resId == 0) {
      return foreground(null);
    }

    return foreground(ComparableResDrawable.create(mComponentContext.getAndroidContext(), resId));
  }

  @Override
  public InternalNode fullImpressionHandler(
      @Nullable EventHandler<FullImpressionVisibleEvent> fullImpressionHandler) {
    mPrivateFlags |= PFLAG_FULL_IMPRESSION_HANDLER_IS_SET;
    mFullImpressionHandler = addVisibilityHandler(mFullImpressionHandler, fullImpressionHandler);
    return this;
  }

  @Override
  public int[] getBorderColors() {
    return mBorderColors;
  }

  @Override
  public @Nullable PathEffect getBorderPathEffect() {
    return mBorderPathEffect;
  }

  @Override
  public float[] getBorderRadius() {
    return mBorderRadius;
  }

  @Override
  public @Nullable InternalNode getChildAt(int index) {
    if (mYogaNode.getChildAt(index) == null) {
      return null;
    }
    return (InternalNode) mYogaNode.getChildAt(index).getData();
  }

  @Override
  public int getChildCount() {
    return mYogaNode.getChildCount();
  }

  @Override
  public int getChildIndex(InternalNode child) {
    for (int i = 0, count = mYogaNode.getChildCount(); i < count; i++) {
      if (mYogaNode.getChildAt(i) == child.getYogaNode()) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Return the list of components contributing to this InternalNode. We have no need for this in
   * production but it is useful information to have while debugging. Therefor this list will only
   * contain the root component if running in production mode.
   */
  @Override
  public List<Component> getComponents() {
    return mComponents;
  }

  @Override
  public @Nullable ArrayList<Component> getComponentsNeedingPreviousRenderData() {
    return mComponentsNeedingPreviousRenderData;
  }

  @Override
  public ComponentContext getContext() {
    return mComponentContext;
  }

  @Override
  public @Nullable DiffNode getDiffNode() {
    return mDiffNode;
  }

  @Override
  public void setDiffNode(@Nullable DiffNode diffNode) {
    mDiffNode = diffNode;
  }

  @Override
  public @Nullable EventHandler<FocusedVisibleEvent> getFocusedHandler() {
    return mFocusedHandler;
  }

  @Override
  public @Nullable ComparableDrawable getForeground() {
    return mForeground;
  }

  @Override
  public @Nullable EventHandler<FullImpressionVisibleEvent> getFullImpressionHandler() {
    return mFullImpressionHandler;
  }

  @Override
  public int getImportantForAccessibility() {
    return mImportantForAccessibility;
  }

  @Nullable
  @Override
  public EventHandler<InvisibleEvent> getInvisibleHandler() {
    return mInvisibleHandler;
  }

  @Override
  public int getLastHeightSpec() {
    return mLastHeightSpec;
  }

  @Override
  public void setLastHeightSpec(int heightSpec) {
    mLastHeightSpec = heightSpec;
  }

  /**
   * The last value the measure funcion associated with this node {@link Component} returned for the
   * height. This is used together with {@link InternalNode#getLastHeightSpec()} to implement
   * measure caching.
   */
  @Override
  public float getLastMeasuredHeight() {
    return mLastMeasuredHeight;
  }

  /**
   * Sets the last value the measure funcion associated with this node {@link Component} returned
   * for the height.
   */
  @Override
  public void setLastMeasuredHeight(float lastMeasuredHeight) {
    mLastMeasuredHeight = lastMeasuredHeight;
  }

  /**
   * The last value the measure funcion associated with this node {@link Component} returned for the
   * width. This is used together with {@link InternalNode#getLastWidthSpec()} to implement measure
   * caching.
   */
  @Override
  public float getLastMeasuredWidth() {
    return mLastMeasuredWidth;
  }

  /**
   * Sets the last value the measure funcion associated with this node {@link Component} returned
   * for the width.
   */
  @Override
  public void setLastMeasuredWidth(float lastMeasuredWidth) {
    mLastMeasuredWidth = lastMeasuredWidth;
  }

  @Override
  public int getLastWidthSpec() {
    return mLastWidthSpec;
  }

  @Override
  public void setLastWidthSpec(int widthSpec) {
    mLastWidthSpec = widthSpec;
  }

  @Override
  public int getLayoutBorder(YogaEdge edge) {
    return FastMath.round(mYogaNode.getLayoutBorder(edge));
  }

  @Override
  public float getMaxHeight() {
    return mYogaNode.getMaxHeight().value;
  }

  @Override
  public float getMaxWidth() {
    return mYogaNode.getMaxWidth().value;
  }

  @Override
  public float getMinHeight() {
    return mYogaNode.getMinHeight().value;
  }

  @Override
  public float getMinWidth() {
    return mYogaNode.getMinWidth().value;
  }

  @Override
  public @Nullable InternalNode getNestedTree() {
    return mNestedTreeProps != null ? mNestedTreeProps.mNestedTree : null;
  }

  /**
   * Set the nested tree before measuring it in order to transfer over important information such as
   * layout direction needed during measurement.
   */
  @Override
  public void setNestedTree(InternalNode nestedTree) {
    if (nestedTree != NULL_LAYOUT) {
      nestedTree.getOrCreateNestedTreeProps().mNestedTreeHolder = this;
    }
    getOrCreateNestedTreeProps().mNestedTree = nestedTree;
  }

  @Override
  public @Nullable InternalNode getNestedTreeHolder() {
    return mNestedTreeProps != null ? mNestedTreeProps.mNestedTreeHolder : null;
  }

  @Override
  public @Nullable NodeInfo getNodeInfo() {
    return mNodeInfo;
  }

  @Override
  public void setNodeInfo(NodeInfo nodeInfo) {
    mNodeInfo = nodeInfo;
  }

  @Override
  public NestedTreeProps getOrCreateNestedTreeProps() {
    if (mNestedTreeProps == null) {
      mNestedTreeProps = new NestedTreeProps();
    }
    return mNestedTreeProps;
  }

  @Override
  public NodeInfo getOrCreateNodeInfo() {
    if (mNodeInfo == null) {
      if (ComponentsConfiguration.isSparseNodeInfoIsEnabled) {
        mNodeInfo = new SparseNodeInfo();
      } else {
        mNodeInfo = new DefaultNodeInfo();
      }
    }

    return mNodeInfo;
  }

  @Override
  public @Nullable InternalNode getParent() {
    if (mYogaNode == null || mYogaNode.getOwner() == null) {
      return null;
    }
    return (InternalNode) mYogaNode.getOwner().getData();
  }

  @Override
  public @Nullable TreeProps getPendingTreeProps() {
    return mNestedTreeProps != null ? mNestedTreeProps.mPendingTreeProps : null;
  }

  @Override
  public @Nullable Component getRootComponent() {
    return mComponents.isEmpty() ? null : mComponents.get(0);
  }

  @Override
  public void setRootComponent(Component component) {
    mComponents.clear();
    mComponents.add(component);
  }

  @Override
  public @Nullable StateListAnimator getStateListAnimator() {
    return mStateListAnimator;
  }

  @Override
  public @DrawableRes int getStateListAnimatorRes() {
    return mStateListAnimatorRes;
  }

  @Override
  public YogaDirection getStyleDirection() {
    return mYogaNode.getStyleDirection();
  }

  @Override
  public float getStyleHeight() {
    return mYogaNode.getHeight().value;
  }

  @Override
  public float getStyleWidth() {
    return mYogaNode.getWidth().value;
  }

  /**
   * A unique identifier which may be set for retrieving a component and its bounds when testing.
   */
  @Override
  public @Nullable String getTestKey() {
    return mTestKey;
  }

  @Override
  public @Nullable Edges getTouchExpansion() {
    return mTouchExpansion;
  }

  @Override
  public int getTouchExpansionBottom() {
    if (!shouldApplyTouchExpansion()) {
      return 0;
    }

    return FastMath.round(mTouchExpansion.get(YogaEdge.BOTTOM));
  }

  @Override
  public int getTouchExpansionLeft() {
    if (!shouldApplyTouchExpansion()) {
      return 0;
    }

    if (YogaConstants.isUndefined(mResolvedTouchExpansionLeft)) {
      mResolvedTouchExpansionLeft = resolveHorizontalEdges(mTouchExpansion, YogaEdge.LEFT);
    }

    return FastMath.round(mResolvedTouchExpansionLeft);
  }

  @Override
  public int getTouchExpansionRight() {
    if (!shouldApplyTouchExpansion()) {
      return 0;
    }

    if (YogaConstants.isUndefined(mResolvedTouchExpansionRight)) {
      mResolvedTouchExpansionRight = resolveHorizontalEdges(mTouchExpansion, YogaEdge.RIGHT);
    }

    return FastMath.round(mResolvedTouchExpansionRight);
  }

  @Override
  public int getTouchExpansionTop() {
    if (!shouldApplyTouchExpansion()) {
      return 0;
    }

    return FastMath.round(mTouchExpansion.get(YogaEdge.TOP));
  }

  @Override
  public @Nullable String getTransitionKey() {
    return mTransitionKey;
  }

  @Override
  public @Nullable Transition.TransitionKeyType getTransitionKeyType() {
    return mTransitionKeyType;
  }

  @Override
  public @Nullable ArrayList<Transition> getTransitions() {
    return mTransitions;
  }

  @Override
  public @Nullable EventHandler<UnfocusedVisibleEvent> getUnfocusedHandler() {
    return mUnfocusedHandler;
  }

  @Override
  public @Nullable EventHandler<VisibilityChangedEvent> getVisibilityChangedHandler() {
    return mVisibilityChangedHandler;
  }

  @Override
  public @Nullable EventHandler<VisibleEvent> getVisibleHandler() {
    return mVisibleHandler;
  }

  @Override
  public float getVisibleHeightRatio() {
    return mVisibleHeightRatio;
  }

  @Override
  public float getVisibleWidthRatio() {
    return mVisibleWidthRatio;
  }

  @Override
  public @Nullable ArrayList<WorkingRangeContainer.Registration> getWorkingRangeRegistrations() {
    return mWorkingRangeRegistrations;
  }

  @Override
  public YogaNode getYogaNode() {
    return mYogaNode;
  }

  @Override
  public boolean hasBorderColor() {
    for (int color : mBorderColors) {
      if (color != Color.TRANSPARENT) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean hasNestedTree() {
    return mNestedTreeProps != null && mNestedTreeProps.mNestedTree != null;
  }

  @Override
  public boolean hasNewLayout() {
    return mYogaNode.hasNewLayout();
  }

  @Override
  public boolean hasStateListAnimatorResSet() {
    return (mPrivateFlags & PFLAG_STATE_LIST_ANIMATOR_RES_SET) != 0;
  }

  @Override
  public boolean hasTouchExpansion() {
    return ((mPrivateFlags & PFLAG_TOUCH_EXPANSION_IS_SET) != 0L);
  }

  @Override
  public boolean hasTransitionKey() {
    return !TextUtils.isEmpty(mTransitionKey);
  }

  @Override
  public boolean hasVisibilityHandlers() {
    return mVisibleHandler != null
        || mFocusedHandler != null
        || mUnfocusedHandler != null
        || mFullImpressionHandler != null
        || mInvisibleHandler != null
        || mVisibilityChangedHandler != null;
  }

  // Used by stetho to re-set auto value
  @Override
  public InternalNode heightAuto() {
    mYogaNode.setHeightAuto();
    return this;
  }

  @Override
  public InternalNode heightPercent(float percent) {
    mPrivateFlags |= PFLAG_HEIGHT_IS_SET;
    mYogaNode.setHeightPercent(percent);
    return this;
  }

  @Override
  public InternalNode heightPx(@Px int height) {
    mPrivateFlags |= PFLAG_HEIGHT_IS_SET;
    mYogaNode.setHeight(height);
    return this;
  }

  @Override
  public InternalNode importantForAccessibility(int importantForAccessibility) {
    mPrivateFlags |= PFLAG_IMPORTANT_FOR_ACCESSIBILITY_IS_SET;
    mImportantForAccessibility = importantForAccessibility;
    return this;
  }

  @Override
  public InternalNode invisibleHandler(@Nullable EventHandler<InvisibleEvent> invisibleHandler) {
    mPrivateFlags |= PFLAG_INVISIBLE_HANDLER_IS_SET;
    mInvisibleHandler = addVisibilityHandler(mInvisibleHandler, invisibleHandler);
    return this;
  }

  @Override
  public boolean isDuplicateParentStateEnabled() {
    return mDuplicateParentState;
  }

  @Override
  public boolean isForceViewWrapping() {
    return mForceViewWrapping;
  }

  @Override
  public boolean isImportantForAccessibilityIsSet() {
    return (mPrivateFlags & PFLAG_IMPORTANT_FOR_ACCESSIBILITY_IS_SET) == 0L
        || mImportantForAccessibility == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
  }

  /**
   * For testing and debugging purposes only where initialization may have not occurred. For any
   * production use, this should never be necessary.
   */
  @Override
  public boolean isInitialized() {
    return mYogaNode != null && mComponentContext != null;
  }

  @Override
  public boolean isLayoutDirectionSet() {
    return (mPrivateFlags & PFLAG_LAYOUT_DIRECTION_IS_SET) == 0L
        || getResolvedLayoutDirection() == YogaDirection.INHERIT;
  }

  /**
   * @return Whether this node is holding a nested tree or not. The decision was made during tree
   *     creation {@link ComponentLifecycle#createLayout(ComponentContext, boolean)}.
   */
  @Override
  public boolean isNestedTreeHolder() {
    return mNestedTreeProps != null && mNestedTreeProps.mIsNestedTreeHolder;
  }

  @Override
  public boolean isPaddingPercent(YogaEdge edge) {
    return mIsPaddingPercent != null && mIsPaddingPercent[edge.intValue()];
  }

  @Override
  public InternalNode isReferenceBaseline(boolean isReferenceBaseline) {
    mYogaNode.setIsReferenceBaseline(isReferenceBaseline);
    return this;
  }

  @Override
  public InternalNode justifyContent(YogaJustify justifyContent) {
    mYogaNode.setJustifyContent(justifyContent);
    return this;
  }

  @Override
  public InternalNode layoutDirection(YogaDirection direction) {
    mPrivateFlags |= PFLAG_LAYOUT_DIRECTION_IS_SET;
    mYogaNode.setDirection(direction);
    return this;
  }

  @Override
  public InternalNode marginAuto(YogaEdge edge) {
    mPrivateFlags |= PFLAG_MARGIN_IS_SET;
    mYogaNode.setMarginAuto(edge);
    return this;
  }

  @Override
  public InternalNode marginPercent(YogaEdge edge, float percent) {
    mPrivateFlags |= PFLAG_MARGIN_IS_SET;
    mYogaNode.setMarginPercent(edge, percent);
    return this;
  }

  @Override
  public InternalNode marginPx(YogaEdge edge, @Px int margin) {
    mPrivateFlags |= PFLAG_MARGIN_IS_SET;
    mYogaNode.setMargin(edge, margin);
    return this;
  }

  /** Mark this node as a nested tree root holder. */
  @Override
  public void markIsNestedTreeHolder(TreeProps currentTreeProps) {
    getOrCreateNestedTreeProps().mIsNestedTreeHolder = true;
    getOrCreateNestedTreeProps().mPendingTreeProps = TreeProps.copy(currentTreeProps);
  }

  @Override
  public void markLayoutSeen() {
    mYogaNode.markLayoutSeen();
  }

  @Override
  public InternalNode maxHeightPercent(float percent) {
    mPrivateFlags |= PFLAG_MAX_HEIGHT_IS_SET;
    mYogaNode.setMaxHeightPercent(percent);
    return this;
  }

  @Override
  public InternalNode maxHeightPx(@Px int maxHeight) {
    mPrivateFlags |= PFLAG_MAX_HEIGHT_IS_SET;
    mYogaNode.setMaxHeight(maxHeight);
    return this;
  }

  @Override
  public InternalNode maxWidthPercent(float percent) {
    mPrivateFlags |= PFLAG_MAX_WIDTH_IS_SET;
    mYogaNode.setMaxWidthPercent(percent);
    return this;
  }

  @Override
  public InternalNode maxWidthPx(@Px int maxWidth) {
    mPrivateFlags |= PFLAG_MAX_WIDTH_IS_SET;
    mYogaNode.setMaxWidth(maxWidth);
    return this;
  }

  @Override
  public InternalNode minHeightPercent(float percent) {
    mPrivateFlags |= PFLAG_MIN_HEIGHT_IS_SET;
    mYogaNode.setMinHeightPercent(percent);
    return this;
  }

  @Override
  public InternalNode minHeightPx(@Px int minHeight) {
    mPrivateFlags |= PFLAG_MIN_HEIGHT_IS_SET;
    mYogaNode.setMinHeight(minHeight);
    return this;
  }

  @Override
  public InternalNode minWidthPercent(float percent) {
    mPrivateFlags |= PFLAG_MIN_WIDTH_IS_SET;
    mYogaNode.setMinWidthPercent(percent);
    return this;
  }

  @Override
  public InternalNode minWidthPx(@Px int minWidth) {
    mPrivateFlags |= PFLAG_MIN_WIDTH_IS_SET;
    mYogaNode.setMinWidth(minWidth);
    return this;
  }

  @Override
  public void padding(Edges padding, @Nullable InternalNode holder) {
    mPrivateFlags |= PFLAG_PADDING_IS_SET;
    for (int i = 0; i < Edges.EDGES_LENGTH; i++) {
      float value = padding.getRaw(i);
      if (!YogaConstants.isUndefined(value)) {
        final YogaEdge edge = YogaEdge.fromInt(i);
        if (holder != null && holder.isPaddingPercent(edge)) {
          mYogaNode.setPaddingPercent(edge, value);
        } else {
          mYogaNode.setPadding(edge, (int) value);
        }
      }
    }
  }

  @Override
  public InternalNode paddingPercent(YogaEdge edge, float percent) {
    mPrivateFlags |= PFLAG_PADDING_IS_SET;

    if (mNestedTreeProps != null && mNestedTreeProps.mIsNestedTreeHolder) {
      getNestedTreePadding().set(edge, percent);
      setIsPaddingPercent(edge, true);
    } else {
      mYogaNode.setPaddingPercent(edge, percent);
    }

    return this;
  }

  @Override
  public InternalNode paddingPx(YogaEdge edge, @Px int padding) {
    mPrivateFlags |= PFLAG_PADDING_IS_SET;

    if (mNestedTreeProps != null && mNestedTreeProps.mIsNestedTreeHolder) {
      getNestedTreePadding().set(edge, padding);
      setIsPaddingPercent(edge, false);
    } else {
      mYogaNode.setPadding(edge, padding);
    }

    return this;
  }

  @Override
  public InternalNode positionPercent(YogaEdge edge, float percent) {
    mPrivateFlags |= PFLAG_POSITION_IS_SET;
    mYogaNode.setPositionPercent(edge, percent);
    return this;
  }

  @Override
  public InternalNode positionPx(YogaEdge edge, @Px int position) {
    mPrivateFlags |= PFLAG_POSITION_IS_SET;
    mYogaNode.setPosition(edge, position);
    return this;
  }

  @Override
  public InternalNode positionType(YogaPositionType positionType) {
    mPrivateFlags |= PFLAG_POSITION_TYPE_IS_SET;
    mYogaNode.setPositionType(positionType);
    return this;
  }

  /** Continually walks the node hierarchy until a node returns a non inherited layout direction */
  @Override
  public YogaDirection recursivelyResolveLayoutDirection() {
    YogaNode yogaNode = mYogaNode;
    while (yogaNode != null && yogaNode.getLayoutDirection() == YogaDirection.INHERIT) {
      yogaNode = yogaNode.getOwner();
    }
    return yogaNode == null ? YogaDirection.INHERIT : yogaNode.getLayoutDirection();
  }

  @Override
  public void registerDebugComponent(DebugComponent debugComponent) {
    if (mDebugComponents == null) {
      mDebugComponents = new HashSet<>();
    }
    mDebugComponents.add(debugComponent);
  }

  @Override
  public InternalNode removeChildAt(int index) {
    return (InternalNode) mYogaNode.removeChildAt(index).getData();
  }

  /** This method marks all resolved layout property values to undefined. */
  @Override
  public void resetResolvedLayoutProperties() {
    mResolvedTouchExpansionLeft = YogaConstants.UNDEFINED;
    mResolvedTouchExpansionRight = YogaConstants.UNDEFINED;
    mResolvedX = YogaConstants.UNDEFINED;
    mResolvedY = YogaConstants.UNDEFINED;
    mResolvedWidth = YogaConstants.UNDEFINED;
    mResolvedHeight = YogaConstants.UNDEFINED;
  }

  @Override
  public void setBorderWidth(YogaEdge edge, @Px int borderWidth) {
    if (mNestedTreeProps != null && mNestedTreeProps.mIsNestedTreeHolder) {
      NestedTreeProps props = getOrCreateNestedTreeProps();
      if (props.mNestedTreeBorderWidth == null) {
        props.mNestedTreeBorderWidth = new Edges();
      }

      props.mNestedTreeBorderWidth.set(edge, borderWidth);
    } else {
      mYogaNode.setBorder(edge, borderWidth);
    }
  }

  @Override
  public void setCachedMeasuresValid(boolean valid) {
    mCachedMeasuresValid = valid;
  }

  @Override
  public void setMeasureFunction(YogaMeasureFunction measureFunction) {
    mYogaNode.setMeasureFunction(measureFunction);
  }

  @Override
  public void setStyleHeightFromSpec(int heightSpec) {
    switch (SizeSpec.getMode(heightSpec)) {
      case SizeSpec.UNSPECIFIED:
        mYogaNode.setHeight(YogaConstants.UNDEFINED);
        break;
      case SizeSpec.AT_MOST:
        mYogaNode.setMaxHeight(SizeSpec.getSize(heightSpec));
        break;
      case SizeSpec.EXACTLY:
        mYogaNode.setHeight(SizeSpec.getSize(heightSpec));
        break;
    }
  }

  @Override
  public void setStyleWidthFromSpec(int widthSpec) {
    switch (SizeSpec.getMode(widthSpec)) {
      case SizeSpec.UNSPECIFIED:
        mYogaNode.setWidth(YogaConstants.UNDEFINED);
        break;
      case SizeSpec.AT_MOST:
        mYogaNode.setMaxWidth(SizeSpec.getSize(widthSpec));
        break;
      case SizeSpec.EXACTLY:
        mYogaNode.setWidth(SizeSpec.getSize(widthSpec));
        break;
    }
  }

  @Override
  public boolean shouldDrawBorders() {
    return hasBorderColor()
        && (mYogaNode.getLayoutBorder(LEFT) != 0
            || mYogaNode.getLayoutBorder(TOP) != 0
            || mYogaNode.getLayoutBorder(RIGHT) != 0
            || mYogaNode.getLayoutBorder(BOTTOM) != 0);
  }

  @Override
  public InternalNode stateListAnimator(@Nullable StateListAnimator stateListAnimator) {
    mPrivateFlags |= PFLAG_STATE_LIST_ANIMATOR_SET;
    mStateListAnimator = stateListAnimator;
    wrapInView();
    return this;
  }

  @Override
  public InternalNode stateListAnimatorRes(@DrawableRes int resId) {
    mPrivateFlags |= PFLAG_STATE_LIST_ANIMATOR_RES_SET;
    mStateListAnimatorRes = resId;
    wrapInView();
    return this;
  }

  @Override
  public InternalNode testKey(@Nullable String testKey) {
    mTestKey = testKey;
    return this;
  }

  @Override
  public InternalNode touchExpansionPx(YogaEdge edge, @Px int touchExpansion) {
    if (mTouchExpansion == null) {
      mTouchExpansion = new Edges();
    }

    mPrivateFlags |= PFLAG_TOUCH_EXPANSION_IS_SET;
    mTouchExpansion.set(edge, touchExpansion);

    return this;
  }

  @Override
  public InternalNode transitionKey(@Nullable String key) {
    if (SDK_INT >= ICE_CREAM_SANDWICH && !TextUtils.isEmpty(key)) {
      mPrivateFlags |= PFLAG_TRANSITION_KEY_IS_SET;
      mTransitionKey = key;
    }

    return this;
  }

  @Override
  public InternalNode transitionKeyType(@Nullable Transition.TransitionKeyType type) {
    mPrivateFlags |= PFLAG_TRANSITION_KEY_TYPE_IS_SET;
    mTransitionKeyType = type;
    return this;
  }

  @Override
  public InternalNode unfocusedHandler(
      @Nullable EventHandler<UnfocusedVisibleEvent> unfocusedHandler) {
    mPrivateFlags |= PFLAG_UNFOCUSED_HANDLER_IS_SET;
    mUnfocusedHandler = addVisibilityHandler(mUnfocusedHandler, unfocusedHandler);
    return this;
  }

  @Override
  public void useHeightAsBaselineFunction(boolean useHeightAsBaselineFunction) {
    if (useHeightAsBaselineFunction) {
      mYogaNode.setBaselineFunction(
          new YogaBaselineFunction() {
            @Override
            public float baseline(YogaNode yogaNode, float width, float height) {
              return height;
            }
          });
    }
  }

  @Override
  public InternalNode visibilityChangedHandler(
      @Nullable EventHandler<VisibilityChangedEvent> visibilityChangedHandler) {
    mPrivateFlags |= PFLAG_VISIBLE_RECT_CHANGED_HANDLER_IS_SET;
    mVisibilityChangedHandler =
        addVisibilityHandler(mVisibilityChangedHandler, visibilityChangedHandler);
    return this;
  }

  @Override
  public InternalNode visibleHandler(@Nullable EventHandler<VisibleEvent> visibleHandler) {
    mPrivateFlags |= PFLAG_VISIBLE_HANDLER_IS_SET;
    mVisibleHandler = addVisibilityHandler(mVisibleHandler, visibleHandler);
    return this;
  }

  @Override
  public InternalNode visibleHeightRatio(float visibleHeightRatio) {
    mVisibleHeightRatio = visibleHeightRatio;
    return this;
  }

  @Override
  public InternalNode visibleWidthRatio(float visibleWidthRatio) {
    mVisibleWidthRatio = visibleWidthRatio;
    return this;
  }

  @Override
  public InternalNode widthAuto() {
    mYogaNode.setWidthAuto();
    return this;
  }

  @Override
  public InternalNode widthPercent(float percent) {
    mPrivateFlags |= PFLAG_WIDTH_IS_SET;
    mYogaNode.setWidthPercent(percent);
    return this;
  }

  @Override
  public InternalNode widthPx(@Px int width) {
    mPrivateFlags |= PFLAG_WIDTH_IS_SET;
    mYogaNode.setWidth(width);
    return this;
  }

  @Override
  public InternalNode wrap(YogaWrap wrap) {
    mYogaNode.setWrap(wrap);
    return this;
  }

  @Override
  public InternalNode wrapInView() {
    mForceViewWrapping = true;
    return this;
  }

  @Px
  @Override
  public int getX() {
    if (YogaConstants.isUndefined(mResolvedX)) {
      mResolvedX = mYogaNode.getLayoutX();
    }

    return (int) mResolvedX;
  }

  @Px
  @Override
  public int getY() {
    if (YogaConstants.isUndefined(mResolvedY)) {
      mResolvedY = mYogaNode.getLayoutY();
    }

    return (int) mResolvedY;
  }

  @Px
  @Override
  public int getWidth() {
    if (YogaConstants.isUndefined(mResolvedWidth)) {
      mResolvedWidth = mYogaNode.getLayoutWidth();
    }

    return (int) mResolvedWidth;
  }

  @Px
  @Override
  public int getHeight() {
    if (YogaConstants.isUndefined(mResolvedHeight)) {
      mResolvedHeight = mYogaNode.getLayoutHeight();
    }

    return (int) mResolvedHeight;
  }

  @Px
  @Override
  public int getPaddingTop() {
    return FastMath.round(mYogaNode.getLayoutPadding(TOP));
  }

  @Px
  @Override
  public int getPaddingRight() {
    return FastMath.round(mYogaNode.getLayoutPadding(RIGHT));
  }

  @Px
  @Override
  public int getPaddingBottom() {
    return FastMath.round(mYogaNode.getLayoutPadding(BOTTOM));
  }

  @Px
  @Override
  public int getPaddingLeft() {
    return FastMath.round(mYogaNode.getLayoutPadding(LEFT));
  }

  @Override
  public boolean isPaddingSet() {
    return (mPrivateFlags & PFLAG_PADDING_IS_SET) != 0L;
  }

  @Override
  public @Nullable ComparableDrawable getBackground() {
    return mBackground;
  }

  @Override
  public YogaDirection getResolvedLayoutDirection() {
    return mYogaNode.getLayoutDirection();
  }

  @Override
  public void copyInto(InternalNode target) {
    if (target == NULL_LAYOUT) {
      return;
    }

    if (mNodeInfo != null) {
      if (target.getNodeInfo() == null) {
        target.setNodeInfo(mNodeInfo);
      } else {
        mNodeInfo.copyInto(target.getOrCreateNodeInfo());
      }
    }
    if (target.isLayoutDirectionSet()) {
      target.layoutDirection(getResolvedLayoutDirection());
    }
    if (target.isImportantForAccessibilityIsSet()) {
      target.importantForAccessibility(mImportantForAccessibility);
    }
    if ((mPrivateFlags & PFLAG_DUPLICATE_PARENT_STATE_IS_SET) != 0L) {
      target.duplicateParentState(mDuplicateParentState);
    }
    if ((mPrivateFlags & PFLAG_BACKGROUND_IS_SET) != 0L) {
      target.background(mBackground);
    }
    if ((mPrivateFlags & PFLAG_FOREGROUND_IS_SET) != 0L) {
      target.foreground(mForeground);
    }
    if (mForceViewWrapping) {
      target.wrapInView();
    }
    if ((mPrivateFlags & PFLAG_VISIBLE_HANDLER_IS_SET) != 0L) {
      target.visibleHandler(mVisibleHandler);
    }
    if ((mPrivateFlags & PFLAG_FOCUSED_HANDLER_IS_SET) != 0L) {
      target.focusedHandler(mFocusedHandler);
    }
    if ((mPrivateFlags & PFLAG_FULL_IMPRESSION_HANDLER_IS_SET) != 0L) {
      target.fullImpressionHandler(mFullImpressionHandler);
    }
    if ((mPrivateFlags & PFLAG_INVISIBLE_HANDLER_IS_SET) != 0L) {
      target.invisibleHandler(mInvisibleHandler);
    }
    if ((mPrivateFlags & PFLAG_UNFOCUSED_HANDLER_IS_SET) != 0L) {
      target.unfocusedHandler(mUnfocusedHandler);
    }
    if ((mPrivateFlags & PFLAG_VISIBLE_RECT_CHANGED_HANDLER_IS_SET) != 0L) {
      target.visibilityChangedHandler(mVisibilityChangedHandler);
    }
    if (mTestKey != null) {
      target.testKey(mTestKey);
    }
    if ((mPrivateFlags & PFLAG_PADDING_IS_SET) != 0L) {
      if (mNestedTreeProps == null || mNestedTreeProps.mNestedTreePadding == null) {
        throw new IllegalStateException(
            "copyInto() must be used when resolving a nestedTree. If padding was set on the holder node, we must have a mNestedTreePadding instance");
      }
      target.padding(mNestedTreeProps.mNestedTreePadding, this);
    }
    if ((mPrivateFlags & PFLAG_BORDER_IS_SET) != 0L) {
      if (mNestedTreeProps == null || mNestedTreeProps.mNestedTreeBorderWidth == null) {
        throw new IllegalStateException(
            "copyInto() must be used when resolving a nestedTree.If border width was set on the holder node, we must have a mNestedTreeBorderWidth instance");
      }

      target.border(mNestedTreeProps.mNestedTreeBorderWidth, mBorderColors, mBorderRadius);
    }
    if ((mPrivateFlags & PFLAG_TRANSITION_KEY_IS_SET) != 0L) {
      target.transitionKey(mTransitionKey);
    }
    if ((mPrivateFlags & PFLAG_TRANSITION_KEY_TYPE_IS_SET) != 0L) {
      target.transitionKeyType(mTransitionKeyType);
    }
    if (mVisibleHeightRatio != 0) {
      target.visibleHeightRatio(mVisibleHeightRatio);
    }
    if (mVisibleWidthRatio != 0) {
      target.visibleWidthRatio(mVisibleWidthRatio);
    }
    if ((mPrivateFlags & PFLAG_STATE_LIST_ANIMATOR_SET) != 0L) {
      target.stateListAnimator(mStateListAnimator);
    }
    if ((mPrivateFlags & PFLAG_STATE_LIST_ANIMATOR_RES_SET) != 0L) {
      target.stateListAnimatorRes(mStateListAnimatorRes);
    }
  }

  private void applyOverridesRecursive(@Nullable InternalNode node) {
    if (ComponentsConfiguration.isDebugModeEnabled && node != null) {
      DebugComponent.applyOverrides(mComponentContext, node);

      for (int i = 0, count = node.getChildCount(); i < count; i++) {
        applyOverridesRecursive(node.getChildAt(i));
      }

      if (node.hasNestedTree()) {
        applyOverridesRecursive(node.getNestedTree());
      }
    }
  }

  @Override
  public void applyAttributes(TypedArray a) {
    for (int i = 0, size = a.getIndexCount(); i < size; i++) {
      final int attr = a.getIndex(i);

      if (attr == R.styleable.ComponentLayout_android_layout_width) {
        int width = a.getLayoutDimension(attr, -1);
        // We don't support WRAP_CONTENT or MATCH_PARENT so no-op for them
        if (width >= 0) {
          widthPx(width);
        }
      } else if (attr == R.styleable.ComponentLayout_android_layout_height) {
        int height = a.getLayoutDimension(attr, -1);
        // We don't support WRAP_CONTENT or MATCH_PARENT so no-op for them
        if (height >= 0) {
          heightPx(height);
        }
      } else if (attr == R.styleable.ComponentLayout_android_minHeight) {
        minHeightPx(a.getDimensionPixelSize(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_minWidth) {
        minWidthPx(a.getDimensionPixelSize(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_paddingLeft) {
        paddingPx(LEFT, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_paddingTop) {
        paddingPx(TOP, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_paddingRight) {
        paddingPx(RIGHT, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_paddingBottom) {
        paddingPx(BOTTOM, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_paddingStart && SUPPORTS_RTL) {
        paddingPx(START, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_paddingEnd && SUPPORTS_RTL) {
        paddingPx(END, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_padding) {
        paddingPx(ALL, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_layout_marginLeft) {
        marginPx(LEFT, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_layout_marginTop) {
        marginPx(TOP, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_layout_marginRight) {
        marginPx(RIGHT, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_layout_marginBottom) {
        marginPx(BOTTOM, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_layout_marginStart && SUPPORTS_RTL) {
        marginPx(START, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_layout_marginEnd && SUPPORTS_RTL) {
        marginPx(END, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_layout_margin) {
        marginPx(ALL, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_importantForAccessibility
          && SDK_INT >= JELLY_BEAN) {
        importantForAccessibility(a.getInt(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_android_duplicateParentState) {
        duplicateParentState(a.getBoolean(attr, false));
      } else if (attr == R.styleable.ComponentLayout_android_background) {
        if (TypedArrayUtils.isColorAttribute(a, R.styleable.ComponentLayout_android_background)) {
          backgroundColor(a.getColor(attr, 0));
        } else {
          backgroundRes(a.getResourceId(attr, -1));
        }
      } else if (attr == R.styleable.ComponentLayout_android_foreground) {
        if (TypedArrayUtils.isColorAttribute(a, R.styleable.ComponentLayout_android_foreground)) {
          foregroundColor(a.getColor(attr, 0));
        } else {
          foregroundRes(a.getResourceId(attr, -1));
        }
      } else if (attr == R.styleable.ComponentLayout_android_contentDescription) {
        getOrCreateNodeInfo().setContentDescription(a.getString(attr));
      } else if (attr == R.styleable.ComponentLayout_flex_direction) {
        flexDirection(YogaFlexDirection.fromInt(a.getInteger(attr, 0)));
      } else if (attr == R.styleable.ComponentLayout_flex_wrap) {
        wrap(YogaWrap.fromInt(a.getInteger(attr, 0)));
      } else if (attr == R.styleable.ComponentLayout_flex_justifyContent) {
        justifyContent(YogaJustify.fromInt(a.getInteger(attr, 0)));
      } else if (attr == R.styleable.ComponentLayout_flex_alignItems) {
        alignItems(YogaAlign.fromInt(a.getInteger(attr, 0)));
      } else if (attr == R.styleable.ComponentLayout_flex_alignSelf) {
        alignSelf(YogaAlign.fromInt(a.getInteger(attr, 0)));
      } else if (attr == R.styleable.ComponentLayout_flex_positionType) {
        positionType(YogaPositionType.fromInt(a.getInteger(attr, 0)));
      } else if (attr == R.styleable.ComponentLayout_flex) {
        final float flex = a.getFloat(attr, -1);
        if (flex >= 0f) {
          flex(flex);
        }
      } else if (attr == R.styleable.ComponentLayout_flex_left) {
        positionPx(LEFT, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_flex_top) {
        positionPx(TOP, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_flex_right) {
        positionPx(RIGHT, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_flex_bottom) {
        positionPx(BOTTOM, a.getDimensionPixelOffset(attr, 0));
      } else if (attr == R.styleable.ComponentLayout_flex_layoutDirection) {
        final int layoutDirection = a.getInteger(attr, -1);
        layoutDirection(YogaDirection.fromInt(layoutDirection));
      }
    }
  }

  @ReturnsOwnership
  private Edges getNestedTreePadding() {
    NestedTreeProps props = getOrCreateNestedTreeProps();
    if (props.mNestedTreePadding == null) {
      props.mNestedTreePadding = new Edges();
    }
    return props.mNestedTreePadding;
  }

  private float resolveHorizontalEdges(Edges spacing, YogaEdge edge) {
    final boolean isRtl = (mYogaNode.getLayoutDirection() == YogaDirection.RTL);

    final YogaEdge resolvedEdge;
    switch (edge) {
      case LEFT:
        resolvedEdge = (isRtl ? YogaEdge.END : YogaEdge.START);
        break;

      case RIGHT:
        resolvedEdge = (isRtl ? YogaEdge.START : YogaEdge.END);
        break;

      default:
        throw new IllegalArgumentException("Not an horizontal padding edge: " + edge);
    }

    float result = spacing.getRaw(resolvedEdge);
    if (YogaConstants.isUndefined(result)) {
      result = spacing.get(edge);
    }

    return result;
  }

  private void setIsPaddingPercent(YogaEdge edge, boolean isPaddingPercent) {
    if (mIsPaddingPercent == null && isPaddingPercent) {
      mIsPaddingPercent = new boolean[YogaEdge.ALL.intValue() + 1];
    }
    if (mIsPaddingPercent != null) {
      mIsPaddingPercent[edge.intValue()] = isPaddingPercent;
    }
  }

  private <T extends Drawable> void setPaddingFromBackground(Drawable drawable) {

    if (drawable != null) {
      final Rect backgroundPadding = new Rect();
      if (getDrawablePadding(drawable, backgroundPadding)) {
        paddingPx(LEFT, backgroundPadding.left);
        paddingPx(TOP, backgroundPadding.top);
        paddingPx(RIGHT, backgroundPadding.right);
        paddingPx(BOTTOM, backgroundPadding.bottom);
      }
    }
  }

  private boolean shouldApplyTouchExpansion() {
    return mTouchExpansion != null && mNodeInfo != null && mNodeInfo.hasTouchEventHandlers();
  }

  /** Crash if the given node has context specific style set. */
  static void assertContextSpecificStyleNotSet(DefaultInternalNode node) {
    List<CharSequence> errorTypes = null;
    if ((node.mPrivateFlags & PFLAG_ALIGN_SELF_IS_SET) != 0L) {
      errorTypes = addOrCreateList(errorTypes, "alignSelf");
    }
    if ((node.mPrivateFlags & PFLAG_POSITION_TYPE_IS_SET) != 0L) {
      errorTypes = addOrCreateList(errorTypes, "positionType");
    }
    if ((node.mPrivateFlags & PFLAG_FLEX_IS_SET) != 0L) {
      errorTypes = addOrCreateList(errorTypes, "flex");
    }
    if ((node.mPrivateFlags & PFLAG_FLEX_GROW_IS_SET) != 0L) {
      errorTypes = addOrCreateList(errorTypes, "flexGrow");
    }
    if ((node.mPrivateFlags & PFLAG_MARGIN_IS_SET) != 0L) {
      errorTypes = addOrCreateList(errorTypes, "margin");
    }

    if (errorTypes != null) {
      final CharSequence errorStr = TextUtils.join(", ", errorTypes);
      final ComponentsLogger logger = node.getContext().getLogger();
      if (logger != null) {
        logger.emitMessage(
            WARNING,
            "You should not set "
                + errorStr
                + " to a root layout in "
                + node.getRootComponent().getClass().getSimpleName());
      }
    }
  }

  static DefaultInternalNode createInternalNode(ComponentContext componentContext) {
    return NodeConfig.sInternalNodeFactory != null
        ? NodeConfig.sInternalNodeFactory.create(componentContext)
        : new DefaultInternalNode(componentContext);
  }

  static YogaNode createYogaNode(ComponentContext componentContext) {
    return componentContext.mYogaNodeFactory != null
        ? componentContext.mYogaNodeFactory.create()
        : NodeConfig.createYogaNode();
  }

  private @Nullable static <T> EventHandler<T> addVisibilityHandler(
      @Nullable EventHandler<T> currentHandler, @Nullable EventHandler<T> newHandler) {
    if (currentHandler == null) {
      return newHandler;
    }
    if (newHandler == null) {
      return currentHandler;
    }
    return new DelegatingEventHandler<>(currentHandler, newHandler);
  }

  /**
   * This is a wrapper on top of built in {@link Drawable#getPadding(Rect)} which overrides default
   * return value. The reason why we need this - is because on pre-L devices LayerDrawable always
   * returns "true" even if drawable doesn't have padding (see https://goo.gl/gExcMQ). Since we
   * heavily rely on correctness of this information, we need to check padding manually
   */
  private static boolean getDrawablePadding(Drawable drawable, Rect outRect) {
    drawable.getPadding(outRect);
    return outRect.bottom != 0 || outRect.top != 0 || outRect.left != 0 || outRect.right != 0;
  }
}