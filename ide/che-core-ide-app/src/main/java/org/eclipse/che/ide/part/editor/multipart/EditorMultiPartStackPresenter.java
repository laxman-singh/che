/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.part.editor.multipart;

import com.google.common.base.Predicate;
import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.ide.api.constraints.Constraints;
import org.eclipse.che.ide.api.editor.AbstractEditorPresenter;
import org.eclipse.che.ide.api.editor.EditorPartPresenter;
import org.eclipse.che.ide.api.event.ActivePartChangedEvent;
import org.eclipse.che.ide.api.event.ActivePartChangedHandler;
import org.eclipse.che.ide.api.event.FileEvent;
import org.eclipse.che.ide.api.parts.EditorPartStack;
import org.eclipse.che.ide.api.parts.PartPresenter;
import org.eclipse.che.ide.api.parts.PartStackView.TabItem;
import org.eclipse.che.ide.part.PartStackPresenter;
import org.eclipse.che.ide.part.PartsComparator;
import org.eclipse.che.ide.part.editor.EditorPartStackFactory;
import org.eclipse.che.ide.part.editor.event.CloseNonPinnedEditorsEvent;
import org.eclipse.che.ide.part.editor.event.CloseNonPinnedEditorsEvent.CloseNonPinnedEditorsHandler;
import org.eclipse.che.ide.part.editor.event.PinEditorTabEvent;
import org.eclipse.che.ide.part.editor.event.PinEditorTabEvent.PinEditorTabEventHandler;
import org.eclipse.che.ide.part.widgets.TabItemFactory;
import org.eclipse.che.ide.api.parts.EditorTab;
import org.eclipse.che.ide.part.widgets.listtab.ListButton;
import org.eclipse.che.ide.part.widgets.listtab.ListItem;
import org.eclipse.che.ide.util.loging.Log;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.google.common.collect.Iterables.filter;
import static org.eclipse.che.ide.api.event.FileEvent.FileOperation.CLOSE;

/**
 * EditorPartStackPresenter is a special PartStackPresenter that is shared among all
 * Perspectives and used to display Editors.
 *
 * @author Nikolay Zamosenchuk
 * @author Stéphane Daviet
 * @author Dmitry Shnurenko
 * @author Vlad Zhukovskyi
 */
@Singleton
public class EditorMultiPartStackPresenter extends PartStackPresenter implements EditorPartStack,
                                                                            ActivePartChangedHandler {
    private final List<EditorPartStack> partStackPresenters;

    private final EventBus                 eventBus;
    private final EditorPartStackFactory   editorPartStackFactory;
    private final EditorMultiPartStackView view;

    private final Map<ListItem, TabItem> items;


    //this list need to save order of added parts
    private final LinkedList<PartPresenter> partsOrder;

    private       EditorPartPresenter       activeEditor;
    private PartPresenter activePart;

    @Inject
    public EditorMultiPartStackPresenter(EditorMultiPartStackView view,
                                         PartsComparator partsComparator,
                                         EventBus eventBus,
                                         EditorPartStackFactory editorPartStackFactory,
                                         TabItemFactory tabItemFactory,
                                         PartStackEventHandler partStackEventHandler) {
        //noinspection ConstantConditions
        super(eventBus, partStackEventHandler, tabItemFactory, partsComparator, view, null);
        this.eventBus = eventBus;
        this.editorPartStackFactory = editorPartStackFactory;

        this.view = view;
//        this.view.setDelegate(this);

        this.items = new HashMap<>();
        this.partStackPresenters = new ArrayList<>();
        this.partsOrder = new LinkedList<>();

//        eventBus.addHandler(PinEditorTabEvent.getType(), this);
//        eventBus.addHandler(CloseNonPinnedEditorsEvent.getType(), this);
        eventBus.addHandler(ActivePartChangedEvent.TYPE, this);
    }

    @Override
    public boolean containsPart(PartPresenter part) {
//        Log.error(getClass(), "=== multipart contains part " + parts);

        for (EditorPartStack partStackPresenter : partStackPresenters) {
            if (partStackPresenter.containsPart(part)) {
                Log.error(getClass(), "=== multipart return true ");
                return true;
            }
        }
        return false;
    }

    @Nullable
    private ListItem getListItemByTab(@NotNull TabItem tabItem) {
        for (Entry<ListItem, TabItem> entry : items.entrySet()) {
            if (tabItem.equals(entry.getValue())) {
                return entry.getKey();
            }
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void setFocus(boolean focused) {
//        Log.error(getClass(), "==== setFocus ===");
//        view.setFocus(focused);
    }

    /** {@inheritDoc} */
    @Override
    public void addPart(@NotNull PartPresenter part, Constraints constraint) {
//        Log.error(getClass(), "====== constraints " + constraint);


        if (constraint == null && activeEditor == null) {
            //not opened files
//            Log.error(getClass(), "====== constraint == null && activeEditor == null");
            EditorPartStack editorPartStack = editorPartStackFactory.create();
            partStackPresenters.add(editorPartStack);


            view.addPartStack(editorPartStack, null, null);
            editorPartStack.addPart(part);
            return;
        }

        if (constraint == null) {
            //activeEditor != null
//            Log.error(getClass(), "====== constraint == null && activeEditor != null");
            for (EditorPartStack partStackPresenter : partStackPresenters) {
                if (partStackPresenter.containsPart(activeEditor)) {
                    partStackPresenter.addPart(part);
                    //todo view.addPartStack(part, null, null);

                }
            }
            return;
        }

        EditorTab relativeTab = null;
        EditorPartStack relatedPartStack = null;

        for (EditorPartStack partStackPresenter : partStackPresenters) {
//            Log.error(getClass(), "====== before relativeTAB " + relativeTab);
            relativeTab = partStackPresenter.getTabById(constraint.relativeId);
            if (relativeTab != null) {
//                Log.error(getClass(), "relativeTab != null");
            }

            if (relativeTab != null && relativeTab.getId().equals(constraint.relativeId)) {
//                Log.error(getClass(), "====== break ");
                relatedPartStack = partStackPresenter;
                break;
            }
        }

        if (relatedPartStack != null && constraint.direction == null) {
            //open new file in the same editor part stack
//            Log.error(getClass(), "====== relatedPartStack != null");
            relatedPartStack.addPart(part, constraint);
            EditorTab tab = (EditorTab)relatedPartStack.getTabByPart(part);
//            view.addPartStack(, relatedPartStack, constraint);
//            activePartStack = relatedPartStack;
            return;
        }

        if (relatedPartStack != null && constraint.direction != null) {
//            Log.error(getClass(), "====== relatedPartStack != null && constraint.direction != null");
            //split vertically/horizontally in the same editor part stack
            EditorPartStack editorPartStack = editorPartStackFactory.create();
//            activePartStack = editorPartStack;
            partStackPresenters.add(editorPartStack);

            view.addPartStack(editorPartStack, relatedPartStack, constraint);
            editorPartStack.addPart(part);
            return;
        }

    }

    /** {@inheritDoc} */
    @Override
    public void addPart(@NotNull PartPresenter part) {
        addPart(part, null);
    }

    /** {@inheritDoc} */
    @Override
    public PartPresenter getActivePart() {
        return activePart;
    }

    /** {@inheritDoc} */
    @Override
    public void setActivePart(@NotNull PartPresenter part) {
//        Log.error(getClass(), "**************** setActivePart " + part.getTitle());
        activePart = part;
//        view.selectTab(part);
    }

    /** {@inheritDoc} */
    @Override
    public void onTabClicked(@NotNull TabItem tab) {
        activePart = parts.get(tab);
//        view.selectTab(parts.get(tab));
    }

    /** {@inheritDoc} */
    @Override
    public void removePart(PartPresenter part) {
        super.removePart(part);
        partsOrder.remove(part);
        activePart = partsOrder.isEmpty() ? null : partsOrder.getLast();

        for (EditorPartStack partStackPresenter : partStackPresenters) {
            if (partStackPresenter.containsPart(part)) {
                Log.error(getClass(), "//////// remove //////////");
                partStackPresenter.removePart(part);
                view.removePartStack(partStackPresenter);
                //todo view.removeFromParent(part, null, null);

            }
        }
    }

//    /** {@inheritDoc} */
//    @Override
//    public void onTabClose(@NotNull TabItem tab) {
//        ListItem listItem = getListItemByTab(tab);
//        items.remove(listItem);
//
//        eventBus.fireEvent(new FileEvent(((EditorTab)tab).getFile(), CLOSE));
//    }

//    /** {@inheritDoc} */
//    @Override
//    public void onEditorTabPinned(PinEditorTabEvent event) {
//        for (Entry<TabItem, PartPresenter> entry : parts.entrySet()) {
//            if (entry.getValue() instanceof AbstractEditorPresenter) {
//                AbstractEditorPresenter editor = (AbstractEditorPresenter)entry.getValue();
//
//                if (editor.getEditorInput().getFile().equals(event.getFile())) {
//                    ((EditorTab)entry.getKey()).setPinMark(event.isPin());
//                    return;
//                }
//            }
//        }
//    }
//
//    /** {@inheritDoc} */
//    @Override
//    public void onCloseNonPinnedEditors(CloseNonPinnedEditorsEvent event) {
//        Iterable<TabItem> nonPinned = filter(parts.keySet(), new Predicate<TabItem>() {
//            @Override
//            public boolean apply(@Nullable TabItem input) {
//                return input instanceof EditorTab && !((EditorTab)input).isPinned();
//            }
//        });
//
//        for (final TabItem tabItem : nonPinned) {
//            Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
//                @Override
//                public void execute() {
//                    eventBus.fireEvent(new FileEvent(((EditorTab)tabItem).getFile(), CLOSE));
//                }
//            });
//        }
//    }

    @Override
    public EditorTab getTabById(@NotNull String tabId) {
        return null;
    }

    @Override
    public EditorPartPresenter getPartByTabId(@NotNull String tabId) {
        for(EditorPartStack editorPartStack : partStackPresenters) {
            EditorPartPresenter editorPart = editorPartStack.getPartByTabId(tabId);
            if (editorPart != null) {
                return editorPart;
            }
        }
        return null;
    }

    @Override
    public void onActivePartChanged(ActivePartChangedEvent event) {
//        Log.error(getClass(), "--------11111111------------ onActivePartChanged " + event.getActivePart().getTitle());
        if (event.getActivePart() instanceof EditorPartPresenter) {
//            Log.error(getClass(), "----22222222---------------- onActivePartChanged " + event.getActivePart().getTitle());
            activeEditor = (EditorPartPresenter)event.getActivePart();
        }
    }
}
