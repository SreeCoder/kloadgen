/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.kloadgen.property.editor;

import static net.coru.kloadgen.util.SchemaRegistryKeyHelper.SCHEMA_REGISTRY_SUBJECTS;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyDescriptor;
import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.coru.kloadgen.extractor.SchemaExtractor;
import net.coru.kloadgen.extractor.impl.SchemaExtractorImpl;
import net.coru.kloadgen.model.FieldValueMapping;
import net.coru.kloadgen.util.AutoCompletion;
import net.coru.kloadgen.util.PropsKeysHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jmeter.gui.ClearGui;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.testbeans.gui.GenericTestBeanCustomizer;
import org.apache.jmeter.testbeans.gui.TableEditor;
import org.apache.jmeter.testbeans.gui.TestBeanGUI;
import org.apache.jmeter.testbeans.gui.TestBeanPropertyEditor;
import org.apache.jmeter.threads.JMeterContextService;

@Slf4j
public class SerialisedSubjectPropertyEditor extends PropertyEditorSupport implements ActionListener, TestBeanPropertyEditor, ClearGui {

  private final JButton loadClassBtn = new JButton("Load Subject");

  private final JPanel panel = new JPanel();

  private final SchemaExtractor schemaExtractor = new SchemaExtractorImpl();

  private JComboBox<String> subjectNameComboBox;

  public SerialisedSubjectPropertyEditor() {
    this.init();
  }

  private void init() {
    subjectNameComboBox = new JComboBox<>();
    panel.setLayout(new BorderLayout());
    panel.add(subjectNameComboBox);
    panel.add(loadClassBtn, BorderLayout.AFTER_LINE_ENDS);
    AutoCompletion.enable(subjectNameComboBox);
    this.loadClassBtn.addActionListener(this);
  }

  public SerialisedSubjectPropertyEditor(Object source) {
    super(source);
    this.init();
    this.setValue(source);
  }

  public SerialisedSubjectPropertyEditor(PropertyDescriptor propertyDescriptor) {
    super(propertyDescriptor);
    this.init();
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    String subjectName = Objects.requireNonNull(this.subjectNameComboBox.getSelectedItem()).toString();

    try {
      Pair<String, List<FieldValueMapping>> attributeList = schemaExtractor.flatPropertiesList(subjectName);

      //Get current test GUI component
      TestBeanGUI testBeanGUI = (TestBeanGUI) GuiPackage.getInstance().getCurrentGui();
      Field customizer = TestBeanGUI.class.getDeclaredField(PropsKeysHelper.CUSTOMIZER);
      customizer.setAccessible(true);

      //From TestBeanGUI retrieve Bean Customizer as it includes all editors like ClassPropertyEditor, TableEditor
      GenericTestBeanCustomizer testBeanCustomizer = (GenericTestBeanCustomizer) customizer.get(testBeanGUI);
      Field editors = GenericTestBeanCustomizer.class.getDeclaredField(PropsKeysHelper.EDITORS);
      editors.setAccessible(true);

      //Retrieve TableEditor and set all fields with default values to it
      PropertyEditor[] propertyEditors = (PropertyEditor[]) editors.get(testBeanCustomizer);
      for (PropertyEditor propertyEditor : propertyEditors) {
        if (propertyEditor instanceof TableEditor) {
          TableEditor tableEditor = (TableEditor) propertyEditor;
          propertyEditor.setValue(mergeValue(tableEditor.getValue(), attributeList.getRight()));
        } else if (propertyEditor instanceof SchemaTypePropertyEditor) {
          propertyEditor.setValue(attributeList.getKey());
        }
      }
      JOptionPane.showMessageDialog(null, "Successful retrieving of subject : " + subjectName, "Successful retrieving properties",
                                    JOptionPane.INFORMATION_MESSAGE);
    } catch (IOException | RestClientException | NoSuchFieldException | IllegalAccessException e) {
      JOptionPane.showMessageDialog(null, "Failed retrieve schema properties : " + e.getMessage(), "ERROR: Failed to retrieve properties!",
                                    JOptionPane.ERROR_MESSAGE);
      log.error(e.getMessage(), e);
    }
  }

  @Override
  public void clearGui() {

  }

  @Override
  public void setDescriptor(PropertyDescriptor descriptor) {
    super.setSource(descriptor);
  }

  @Override
  public String getAsText() {
    return Objects.requireNonNull(this.subjectNameComboBox.getSelectedItem()).toString();
  }

  @Override
  public Component getCustomEditor() {
    return this.panel;
  }

  @Override
  public void setAsText(String text) throws IllegalArgumentException {
    if (this.subjectNameComboBox.getModel().getSize() == 0) {
      this.subjectNameComboBox.addItem(text);
    }
    this.subjectNameComboBox.setSelectedItem(text);
  }

  @Override
  public void setValue(Object value) {
    String subjects = JMeterContextService.getContext().getProperties().getProperty(SCHEMA_REGISTRY_SUBJECTS);
    if (Objects.nonNull(subjects)) {
      String[] subjectsList = subjects.split(",");
      subjectNameComboBox.setModel(new DefaultComboBoxModel<>(subjectsList));
    }
    if (value != null) {
      if (this.subjectNameComboBox.getModel().getSize() == 0) {
        this.subjectNameComboBox.addItem((String) value);
      }
      this.subjectNameComboBox.setSelectedItem(value);
    } else {
      this.subjectNameComboBox.setSelectedItem("");
    }

  }

  @Override
  public Object getValue() {
    return this.subjectNameComboBox.getSelectedItem();
  }

  @Override
  public boolean supportsCustomEditor() {
    return true;
  }

  @SuppressWarnings("unchecked")
  protected List<FieldValueMapping> mergeValue(Object tableEditorValue, List<FieldValueMapping> attributeList) {

    if (!(tableEditorValue instanceof ArrayList<?>)) {
      log.error("Table Editor is not array list");
      return attributeList;
    }

    List<FieldValueMapping> fieldValueList;
    try {
      fieldValueList = (ArrayList<FieldValueMapping>) tableEditorValue;
    } catch (Exception e) {
      log.error("Table Editor is not FieldValueMapping list", e);
      return attributeList;
    }

    if (CollectionUtils.isEmpty(fieldValueList)) {
      return attributeList;
    }

    List<FieldValueMapping> result = new ArrayList<>();
    for (FieldValueMapping fieldValue : attributeList) {

      FieldValueMapping existsValue = checkExists(fieldValue, fieldValueList);

      if (existsValue != null) {
        result.add(existsValue);
      } else {
        result.add(fieldValue);
      }

    }

    return result;
  }

  private FieldValueMapping checkExists(FieldValueMapping fieldValue, List<FieldValueMapping> fieldValueList) {

    return IterableUtils.find(fieldValueList,
                              v -> v.getFieldName().equals(fieldValue.getFieldName()) && v.getFieldType().equals(fieldValue.getFieldType()));
  }

}
