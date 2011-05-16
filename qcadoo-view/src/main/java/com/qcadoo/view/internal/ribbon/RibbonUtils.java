/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo Framework
 * Version: 0.4.1
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.view.internal.ribbon;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.qcadoo.view.api.ribbon.RibbonActionItem;
import com.qcadoo.view.api.ribbon.RibbonComboBox;
import com.qcadoo.view.api.ribbon.RibbonComboItem;
import com.qcadoo.view.internal.api.ComponentPattern;
import com.qcadoo.view.internal.api.InternalViewDefinition;
import com.qcadoo.view.internal.api.ViewDefinition;
import com.qcadoo.view.internal.patterns.AbstractComponentPattern;
import com.qcadoo.view.internal.xml.ViewDefinitionParser;
import com.qcadoo.view.internal.xml.ViewDefinitionParserNodeException;

public final class RibbonUtils {

    private final RibbonTemplates ribbonTemplates;

    private static RibbonUtils instance = new RibbonUtils();

    private RibbonUtils() {
        ribbonTemplates = new RibbonTemplates();
    }

    public static RibbonUtils getInstance() {
        return instance;
    }

    public InternalRibbon parseRibbon(final Node ribbonNode, final ViewDefinitionParser parser,
            final ViewDefinition viewDefinition) throws ViewDefinitionParserNodeException {
        InternalRibbon ribbon = new RibbonImpl();

        NodeList childNodes = ribbonNode.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (Node.ELEMENT_NODE != child.getNodeType()) {
                continue;
            }
            if ("group".equals(child.getNodeName())) {
                ribbon.addGroup(parseRibbonGroup(child, parser, viewDefinition));
            } else {
                throw new ViewDefinitionParserNodeException(child, "Wrong node type - 'group' expected");
            }
        }

        return ribbon;
    }

    public JSONObject translateRibbon(final InternalRibbon ribbon, final Locale locale, final AbstractComponentPattern pattern)
            throws JSONException {
        JSONObject json = ribbon.getAsJson();

        for (int i = 0; i < json.getJSONArray("groups").length(); i++) {
            JSONObject group = json.getJSONArray("groups").getJSONObject(i);
            group.put(
                    "label",
                    pattern.getTranslationService().translate(
                            pattern.getTranslationPath() + ".ribbon." + group.getString("name"),
                            "qcadooView.ribbon." + group.getString("name"), locale));
            translateRibbonItems(group, group.getString("name") + ".", locale, pattern);
        }

        return json;
    }

    private void translateRibbonItems(final JSONObject owner, final String prefix, final Locale locale,
            final AbstractComponentPattern pattern) throws JSONException {
        if (owner.has("items")) {
            for (int j = 0; j < owner.getJSONArray("items").length(); j++) {
                JSONObject item = owner.getJSONArray("items").getJSONObject(j);

                String label = pattern.getTranslationService().translate(
                        pattern.getTranslationPath() + ".ribbon." + prefix + item.getString("name"),
                        "qcadooView.ribbon." + prefix + item.getString("name"), locale);
                item.put("label", label);

                if (item.has("script")) {
                    String script = item.getString("script");
                    if (script != null) {
                        item.put("script", pattern.prepareScript(script, locale));
                    }
                }

                if (item.has("message")) {
                    String message = item.getString("message");
                    if (message.contains(".")) {
                        message = pattern.getTranslationService().translate(message, locale);
                    } else {
                        message = pattern.getTranslationService().translate("qcadooView.message." + message, locale);
                    }
                    item.put("message", pattern.prepareScript(message, locale));
                }

                translateRibbonItems(item, prefix + item.getString("name") + ".", locale, pattern);
            }
        }
    }

    public InternalRibbonGroup parseRibbonGroup(final Node groupNode, final ViewDefinitionParser parser,
            final ViewDefinition viewDefinition) throws ViewDefinitionParserNodeException {
        String template = parser.getStringAttribute(groupNode, "template");

        if (template != null) {
            try {
                return ribbonTemplates.getGroupTemplate(template, viewDefinition);
            } catch (IllegalStateException e) {
                throw new ViewDefinitionParserNodeException(groupNode, e);
            }
        } else {
            String groupName = parser.getStringAttribute(groupNode, "name");
            if (groupName == null) {
                throw new ViewDefinitionParserNodeException(groupNode, "Name attribute cannot be empty");
            }
            InternalRibbonGroup ribbonGroup = new RibbonGroupImpl(groupName);

            NodeList childNodes = groupNode.getChildNodes();

            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);

                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    ribbonGroup.addItem(parseRibbonItem(child, parser, viewDefinition));
                }
            }

            return ribbonGroup;
        }
    }

    private InternalRibbonActionItem parseRibbonItem(final Node itemNode, final ViewDefinitionParser parser,
            final ViewDefinition viewDefinition) throws ViewDefinitionParserNodeException {
        String stringType = itemNode.getNodeName();

        RibbonActionItem.Type type = null;
        if ("bigButtons".equals(stringType) || "bigButton".equals(stringType)) {
            type = RibbonActionItem.Type.BIG_BUTTON;
        } else if ("smallButtons".equals(stringType) || "smallButton".equals(stringType)) {
            type = RibbonActionItem.Type.SMALL_BUTTON;
        } else if ("combobox".equals(stringType)) {
            type = RibbonActionItem.Type.COMBOBOX;
        } else if ("smallEmptySpace".equals(stringType)) {
            type = RibbonActionItem.Type.SMALL_EMPTY_SPACE;
        } else {
            throw new ViewDefinitionParserNodeException(itemNode, "Unsupported ribbon item type '" + stringType + "'");
        }

        InternalRibbonActionItem item = null;
        if ("bigButtons".equals(stringType) || "smallButtons".equals(stringType)) {
            item = new RibbonComboItemImpl();
        } else if ("combobox".equals(stringType)) {
            item = new RibbonComboBoxImpl();
        } else {
            item = new RibbonActionItemImpl();
        }

        item.setIcon(parser.getStringAttribute(itemNode, "icon"));
        item.setName(parser.getStringAttribute(itemNode, "name"));
        item.setAction(translateRibbonAction(parser.getStringAttribute(itemNode, "action"), viewDefinition));
        item.setType(type);
        String state = parser.getStringAttribute(itemNode, "state");
        if (state != null) {
            if ("enabled".equals(state)) {
                item.setEnabled(true);
            } else if ("disabled".equals(state)) {
                item.setEnabled(false);
            } else {
                throw new ViewDefinitionParserNodeException(itemNode, "Unsupported ribbon item state : " + state);
            }
        } else {
            item.setEnabled(true);
        }
        String message = parser.getStringAttribute(itemNode, "message");
        if (message != null) {
            item.setMessage(message);
        }

        NodeList childNodes = itemNode.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "script".equals(child.getNodeName())) {
                item.setScript(parser.getStringNodeContent(child));
            }
        }

        if (item instanceof RibbonComboItem) {
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && !"script".equals(child.getNodeName())) {
                    ((InternalRibbonComboItem) item).addItem(parseRibbonItem(child, parser, viewDefinition));
                }
            }
        } else if (item instanceof RibbonComboBox) {
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && !"script".equals(child.getNodeName())) {
                    if (!"option".equals(child.getNodeName())) {
                        throw new ViewDefinitionParserNodeException(child, "ribbon combobox can only have 'option' elements");
                    }
                    ((RibbonComboBox) item).addOption(parser.getStringAttribute(child, "name"));
                }
            }
        } else {
            (item).setAction(translateRibbonAction(parser.getStringAttribute(itemNode, "action"), viewDefinition));
        }

        return item;
    }

    public String translateRibbonAction(final String action, final ViewDefinition viewDefinition) {
        if (action == null) {
            return null;
        }

        Pattern p = Pattern.compile("#\\{([^\\}]+)\\}");
        Matcher m = p.matcher(action);

        String translateAction = action;

        while (m.find()) {
            ComponentPattern actionComponentPattern = ((InternalViewDefinition) viewDefinition).getComponentByReference(m
                    .group(1));

            if (actionComponentPattern == null) {
                throw new IllegalStateException("Cannot find component '" + m.group(1) + "' for action: " + action);
            }

            translateAction = translateAction.replace("#{" + m.group(1) + "}", "#{" + actionComponentPattern.getPath() + "}");
        }

        return translateAction;
    }
}
