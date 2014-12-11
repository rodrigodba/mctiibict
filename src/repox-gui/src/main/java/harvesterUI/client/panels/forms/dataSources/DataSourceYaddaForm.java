package harvesterUI.client.panels.forms.dataSources;

import com.extjs.gxt.ui.client.Style;
import com.extjs.gxt.ui.client.event.ButtonEvent;
import com.extjs.gxt.ui.client.event.SelectionListener;
import com.extjs.gxt.ui.client.mvc.Dispatcher;
import com.extjs.gxt.ui.client.widget.button.Button;
import com.extjs.gxt.ui.client.widget.form.*;
import com.extjs.gxt.ui.client.widget.layout.FitLayout;
import com.extjs.gxt.ui.client.widget.layout.FormData;
import com.google.gwt.user.client.Element;
import harvesterUI.client.HarvesterUI;
import harvesterUI.client.core.AppEvents;
import harvesterUI.client.util.formPanel.EditableFormLayout;
import harvesterUI.shared.dataTypes.DataProviderUI;
import harvesterUI.shared.dataTypes.dataSet.DataSourceUI;
import harvesterUI.shared.dataTypes.dataSet.DatasetType;

import java.util.List;

/**
 * Created to REPOX.
 * User: Edmundo
 * Date: 14-03-2011
 * Time: 14:56
 */
public class DataSourceYaddaForm extends DataSourceForm {

    private FormData formData;
    private FieldSet dataSet;
    private String oldDataSetId = "";

    private TextField<String> exportPath;
    private SimpleComboBox<String> yaddaCollection;

    public DataSourceYaddaForm(FormData data) {
        super(data);
        formData = data;
        dataSourceSchemaForm = new DataSourceSchemaForm();
        setHeaderVisible(false);
        setBodyBorder(false);
        setLayout(new FitLayout());
        setLayoutOnChange(true);

        createFolderForm();

        add(createOutputSet());
    }

    @Override
    protected void onRender(Element parent, int index) {
        super.onRender(parent, index);
        setScrollMode(Style.Scroll.AUTO);
    }

    private void createFolderForm() {
        dataSet = new FieldSet();
        dataSet.setAutoHeight(true);
        dataSet.setAutoWidth(true);
        dataSet.setHeading(HarvesterUI.CONSTANTS.dataSet());

        dataSet.setLayout(new EditableFormLayout(DEFAULT_LABEL_WIDTH));

        dataSourceSchemaForm.addSchemaYaddaFormatPart(dataSet, smallFixedFormData, formData);

        yaddaCollection = new SimpleComboBox<String>();
        yaddaCollection.setFieldLabel("Yadda Collection" + HarvesterUI.REQUIRED_STR);
        yaddaCollection.setTriggerAction(ComboBox.TriggerAction.ALL);
        yaddaCollection.setEditable(false);
        yaddaCollection.setId("yaddaCollection");
        dataSet.add(yaddaCollection, formData);


        exportPath = new TextField<String>();
        exportPath.setFieldLabel(HarvesterUI.CONSTANTS.exportPath());
        exportPath.setId("exportPathField");
        dataSet.add(exportPath, formData);

        Button saveButton = new Button(HarvesterUI.CONSTANTS.save(),HarvesterUI.ICONS.save_icon(),new SelectionListener<ButtonEvent>() {
            @Override
            public void componentSelected(ButtonEvent be) {
                saveData();
            }
        });
        addButton(saveButton);

        addButton(new Button(HarvesterUI.CONSTANTS.cancel(),HarvesterUI.ICONS.cancel_icon(),new SelectionListener<ButtonEvent>() {
            @Override
            public void componentSelected(ButtonEvent be) {
                Dispatcher.forwardEvent(AppEvents.HideDataSourceForm);
            }
        }));

        setButtonAlign(Style.HorizontalAlignment.CENTER);

        FormButtonBinding binding = new FormButtonBinding(this);
        binding.addButton(saveButton);

        add(dataSet);
    }

    public void setEditMode(DataSourceUI ds) {
        dataSourceUI = ds;
        fillMetadataComboStore(true);
        edit = true;
        oldDataSetId = ds.getDataSourceSet();

        editTagsContainer(dataSourceUI);
        setEditTransformationCombo(dataSourceUI);

        dataSourceSchemaForm.getSchema().setValue(dataSourceUI.getSchema());
        dataSourceSchemaForm.getMetadataNamespace().setValue(dataSourceUI.getMetadataNamespace());
        dataSourceSchemaForm.editMarcCombo(dataSourceUI.getMarcFormat());
        recordSet.setValue(dataSourceUI.getDataSourceSet());
        description.setValue(dataSourceUI.getDescription());
        exportPath.setValue(dataSourceUI.getExportDirectory());
        yaddaCollection.setSimpleValue(dataSourceUI.getYaddaCollection());
    }

    public void resetValues(DataProviderUI parent){
        fillMetadataComboStore(false);
        edit = false;
        oldDataSetId = "";
        setResetNamespaces();
        setResetOutputSet(parent);
        dataSourceServicesPanel.resetValues();
        exportPath.clear();
        yaddaCollection.setValue(yaddaCollection.getStore().getAt(0));
    }

    public String getMetadataFormat() {
        return dataSourceSchemaForm.getMetadataFormatCombo().getValue().getShortDesignation();
    }

    @Override
    public String getFolderPath() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getSchema() {
        return dataSourceSchemaForm.getSchema().getValue();
    }

    private void fillMetadataComboStore(final boolean edit){
        dataSourceSchemaForm.loadMetadataFormatComboSchemas(edit, dataSourceUI);
    }

    public void saveData(){
        String metadataFormat = dataSourceSchemaForm.getMetadataFormatCombo().getValue().getShortDesignation();
        String idPolicy = "IdGenerated";
        String dsRetrieveStrat = "pt.utl.ist.repox.marc.DataSourceFolder";
        String record_set = recordSet.getValue();
        String desc = description.getValue();

        if(dataSourceUI == null) {
            dataSourceUI = new DataSourceUI(parent, desc.trim(), "", metadataFormat.trim() + " | ese", "Folder " + metadataFormat.trim(),
                    parent.getCountry().trim(),desc.trim(), "", "", "",
                    "", idPolicy.trim(), metadataFormat.trim());
        }

        dataSourceUI.setIngest("Folder " + metadataFormat.trim());
        dataSourceUI.setSourceMDFormat(metadataFormat.trim());
        dataSourceUI.setRecordIdPolicy(idPolicy.trim());
        if(dataSourceUI.getFolderPath() != null && (dataSourceUI.getFolderPath().startsWith("\\")
                || dataSourceUI.getFolderPath().startsWith("/"))){
            dataSourceUI.setFolderPath(dataSourceUI.getFolderPath().substring(1));
        }
        dataSourceUI.setDataSourceSet(record_set.trim());

        List<String> namespaces = namespacePanelExtension.getFinalNamespacesList();

        dataSourceUI.setNamespaceList(namespaces);
        dataSourceUI.setRetrieveStartegy(dsRetrieveStrat);
        dataSourceUI.setExportDirectory(exportPath.getValue() != null ? exportPath.getValue().trim() : "");
        dataSourceUI.setYaddaCollection(yaddaCollection.getSimpleValue().trim());

        dataSourceUI.setMarcFormat(dataSourceSchemaForm.getMarcFormat().trim());
        saveDataSource(dataSourceUI,oldDataSetId, DatasetType.YADDA,dataSourceSchemaForm.getSchema().getValue(),dataSourceSchemaForm.getMetadataNamespace().getValue(),
                metadataFormat,"","","");
    }
}

