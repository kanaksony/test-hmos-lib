package com.hardsoftstudio.anchorsheetlayout.slice;

import com.hardsoftstudio.anchorsheetlayout.ResourceTable;
import com.hardsoftstudio.anchorsheetlayout.SampleItem;
import com.hardsoftstudio.anchorsheetlayout.SampleItemProvider;
import com.hardsoftstudio.anchorsheetlayout.AnchorSheetLayout;
import com.hardsoftstudio.anchorsheetlayout.NonNull;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.*;
import java.util.ArrayList;
import java.util.List;

public class MainAbilitySlice extends AbilitySlice {

    private AnchorSheetLayout anchorSheetLayout;

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);
        initListContainer();
        Button button = (Button) findComponentById(ResourceTable.Id_tap_me);
        anchorSheetLayout = (AnchorSheetLayout) findComponentById(ResourceTable.Id_anchorsheet_layout);
        anchorSheetLayout.setState(AnchorSheetLayout.STATE_COLLAPSED);
        anchorSheetLayout.setHideable(true);
        Text content = (Text) findComponentById(ResourceTable.Id_content);
        button.setClickedListener(component -> {
            switch (anchorSheetLayout.getState()) {
                case AnchorSheetLayout.STATE_ANCHOR:
                    anchorSheetLayout.setState(AnchorSheetLayout.STATE_FORCE_HIDDEN);
                    break;
                case AnchorSheetLayout.STATE_COLLAPSED:
                case AnchorSheetLayout.STATE_EXPANDED:
                    anchorSheetLayout.setState(AnchorSheetLayout.STATE_ANCHOR);
                    break;
                case AnchorSheetLayout.STATE_HIDDEN:
                case AnchorSheetLayout.STATE_FORCE_HIDDEN:
                    anchorSheetLayout.setState(AnchorSheetLayout.STATE_COLLAPSED);
                    break;
                default:
                    break;
            }
        });

        anchorSheetLayout.setAnchorSheetCallback(new AnchorSheetLayout.AnchorSheetCallback() {

            @Override
            public void onStateChanged(@NonNull Component bottomSheet, @AnchorSheetLayout.State int newState) {
                //content.setText(newState == AnchorSheetBehavior.STATE_EXPANDED ? ResourceTable.String_slide_me_down : ResourceTable.String_slide_me_up);
                switch (newState) {
                    case AnchorSheetLayout.STATE_ANCHOR:
                        content.setText("Anchor State");
                        break;
                    case AnchorSheetLayout.STATE_COLLAPSED:
                        content.setText("Collapsed State");
                        break;
                    case AnchorSheetLayout.STATE_HIDDEN:
                        content.setText("Hidden State");
                        break;
                    case AnchorSheetLayout.STATE_EXPANDED:
                        content.setText("Expanded State");
                        break;
                    case AnchorSheetLayout.STATE_DRAGGING:
                        content.setText("Dragging State");
                        break;
                    case AnchorSheetLayout.STATE_SETTLING:
                        content.setText("Settling State");
                        break;
                    default:
                        break;
                }

            }

            @Override
            public void onSlide(@NonNull Component bottomSheet, float slideOffset) {

            }
        });

    }

    private void initListContainer() {
        ListContainer listContainer = (ListContainer) findComponentById(ResourceTable.Id_list_container);
        ListContainer listContainer1 = (ListContainer) findComponentById(ResourceTable.Id_list_container1);
        List<SampleItem> list = getData();
        SampleItemProvider sampleItemProvider = new SampleItemProvider(list, this);
        listContainer.setItemProvider(sampleItemProvider);
        listContainer1.setItemProvider(sampleItemProvider);
    }
    private ArrayList<SampleItem> getData() {
        ArrayList<SampleItem> list = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            list.add(new SampleItem("Item" + i));
        }
        return list;
    }

    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }

}
