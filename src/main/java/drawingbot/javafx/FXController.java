package drawingbot.javafx;

import drawingbot.DrawingBotV3;
import drawingbot.FXApplication;
import drawingbot.api.Hooks;
import drawingbot.api.IDrawingPen;
import drawingbot.api.IDrawingSet;
import drawingbot.files.*;
import drawingbot.drawing.*;
import drawingbot.files.json.presets.*;
import drawingbot.javafx.observables.ObservableDrawingSet;
import drawingbot.javafx.observables.ObservableImageFilter;
import drawingbot.image.blend.EnumBlendMode;
import drawingbot.integrations.vpype.FXVPypeController;
import drawingbot.integrations.vpype.VpypeHelper;
import drawingbot.javafx.controls.*;
import drawingbot.javafx.observables.ObservableDrawingPen;
import drawingbot.javafx.observables.ObservableProjectSettings;
import drawingbot.pfm.PFMFactory;
import drawingbot.registry.MasterRegistry;
import drawingbot.plotting.PlottingTask;
import drawingbot.registry.Register;
import drawingbot.render.IDisplayMode;
import drawingbot.utils.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.control.*;

import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxListCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.FloatStringConverter;
import javafx.util.converter.IntegerStringConverter;
import javafx.util.converter.NumberStringConverter;
import org.controlsfx.control.RangeSlider;
import org.controlsfx.glyphfont.FontAwesome;
import org.controlsfx.glyphfont.Glyph;

import java.awt.image.BufferedImageOp;
import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;

public class FXController {

    /**
     * starts the FXController, called internally by JavaFX
     */
    public void initialize(){
        DrawingBotV3.logger.entering("FX Controller", "initialize");

        try{
            Hooks.runHook(Hooks.FX_CONTROLLER_PRE_INIT, this);
            initToolbar();
            initViewport();
            initPlottingControls();
            initProgressBar();
            initDrawingAreaPane();
            initPreProcessingPane();
            initPFMControls();
            initPenSettingsPane();
            initVersionControlPane();
            initBatchProcessingPane();
            Hooks.runHook(Hooks.FX_CONTROLLER_POST_INIT, this);

            viewportScrollPane.setHvalue(0.5);
            viewportScrollPane.setVvalue(0.5);

            viewportScrollPane.setOnMouseMoved(DrawingBotV3.INSTANCE::onMouseMovedViewport);
            viewportScrollPane.setOnMousePressed(DrawingBotV3.INSTANCE::onMousePressedViewport);
            viewportScrollPane.setOnKeyPressed(DrawingBotV3.INSTANCE::onKeyPressedViewport);



            initSeparateStages();

            DrawingBotV3.INSTANCE.blendMode.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.reRender());
            DrawingBotV3.INSTANCE.currentFilters.addListener((ListChangeListener<ObservableImageFilter>) c -> DrawingBotV3.INSTANCE.onImageFiltersChanged());

        }catch (Exception e){
            DrawingBotV3.logger.log(Level.SEVERE, "Failed to initialize JAVA FX", e);
        }

        DrawingBotV3.logger.exiting("FX Controller", "initialize");
    }

    public Stage exportSettingsStage;
    public FXExportController exportController;

    public Stage vpypeSettingsStage;
    public FXVPypeController vpypeController;

    public Stage mosaicSettingsStage;
    public FXStylesController mosaicController;

    public Stage taskMonitorStage;
    public FXTaskMonitorController taskMonitorController;

    public Stage projectManagerStage;
    public FXProjectManagerController projectManagerController;


    public void initSeparateStages() {
        FXHelper.initSeparateStage("/fxml/exportsettings.fxml", exportSettingsStage = new Stage(), exportController = new FXExportController(), "Export Settings", Modality.APPLICATION_MODAL);
        FXHelper.initSeparateStage("/fxml/vpypesettings.fxml", vpypeSettingsStage = new Stage(), vpypeController = new FXVPypeController(), "vpype Settings", Modality.APPLICATION_MODAL);
        FXHelper.initSeparateStage("/fxml/mosaicsettings.fxml", mosaicSettingsStage = new Stage(), mosaicController = new FXStylesController(), "Mosaic Settings", Modality.APPLICATION_MODAL);
        FXHelper.initSeparateStage("/fxml/serialportsettings.fxml", (Stage) Hooks.runHook(Hooks.SERIAL_CONNECTION_STAGE, new Stage())[0], Hooks.runHook(Hooks.SERIAL_CONNECTION_CONTROLLER, new DummyController())[0], "Plotter / Serial Port Connection", Modality.NONE);
        FXHelper.initSeparateStage("/fxml/taskmonitor.fxml", taskMonitorStage = new Stage(), taskMonitorController = new FXTaskMonitorController(), "Task Monitor", Modality.NONE);
        FXHelper.initSeparateStage("/fxml/projectmanager.fxml", projectManagerStage = new Stage(), projectManagerController = new FXProjectManagerController(), "Project Manager", Modality.NONE);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////GLOBAL CONTAINERS
    public VBox vBoxMain = null;

    public ScrollPane scrollPaneSettings = null;
    public VBox vBoxSettings = null;

    public DialogPresetRename presetEditorDialog = new DialogPresetRename();

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //// TOOL BAR

    public Menu menuFile = null;
    public Menu menuView = null;
    public Menu menuFilters = null;
    public Menu menuHelp = null;

    public Map<TitledPane, Stage> settingsStages = new LinkedHashMap<>();
    public Map<TitledPane, Node> settingsContent = new LinkedHashMap<>();

    public void initToolbar(){
        //file
        MenuItem menuOpen = new MenuItem("Open Project");
        menuOpen.setOnAction(e -> FXHelper.importPreset(Register.PRESET_TYPE_PROJECT, true, false));
        menuFile.getItems().add(menuOpen);

        MenuItem menuSave = new MenuItem("Save Project");
        menuSave.disableProperty().bind(DrawingBotV3.INSTANCE.activeTask.isNull());
        menuSave.setOnAction(e -> {
            FXHelper.exportProject(DrawingBotV3.INSTANCE.activeTask.get().originalFile.getParentFile(), FileUtils.removeExtension(DrawingBotV3.INSTANCE.activeTask.get().originalFile.getName()));
        });
        menuFile.getItems().add(menuSave);

        menuFile.getItems().add(new SeparatorMenuItem());
        /*
        MenuItem projectManager = new MenuItem("Open Project Manager");
        projectManager.setOnAction(e -> projectManagerStage.show());
        menuFile.getItems().add(projectManager);

        menuFile.getItems().add(new SeparatorMenuItem());

         */


        MenuItem menuImport = new MenuItem("Import Image");
        menuImport.setOnAction(e -> FXHelper.importImageFile());
        menuFile.getItems().add(menuImport);

        MenuItem menuVideo = new MenuItem("Import Video");
        menuVideo.setOnAction(e -> FXHelper.importVideoFile());
        menuFile.getItems().add(menuVideo);

        menuFile.getItems().add(new SeparatorMenuItem());

        Menu menuExport = new Menu("Export per/drawing");
        Menu menuExportPerPen = new Menu("Export per/pen");
        Menu menuExportPerGroup = new Menu("Export per/group");

        for(DrawingExportHandler.Category category : DrawingExportHandler.Category.values()){
            for(DrawingExportHandler format : MasterRegistry.INSTANCE.drawingExportHandlers){
                if(format.category != category){
                    continue;
                }
                if(format.isPremium && !FXApplication.isPremiumEnabled){
                    MenuItem item = new MenuItem(format.displayName + " (Premium)");
                    item.setOnAction(e -> showPremiumFeatureDialog());
                    menuExport.getItems().add(item);

                    MenuItem itemPerPen = new MenuItem(format.displayName + " (Premium)");
                    itemPerPen.setOnAction(e -> showPremiumFeatureDialog());
                    menuExportPerPen.getItems().add(itemPerPen);

                    MenuItem itemPerGroup = new MenuItem(format.displayName + " (Premium)");
                    itemPerGroup.setOnAction(e -> showPremiumFeatureDialog());
                    menuExportPerGroup.getItems().add(itemPerGroup);
                }else{
                    MenuItem item = new MenuItem(format.displayName);
                    item.setOnAction(e -> FXHelper.exportFile(format, ExportTask.Mode.PER_DRAWING));
                    menuExport.getItems().add(item);

                    MenuItem itemPerPen = new MenuItem(format.displayName);
                    itemPerPen.setOnAction(e -> FXHelper.exportFile(format, ExportTask.Mode.PER_PEN));
                    menuExportPerPen.getItems().add(itemPerPen);

                    MenuItem itemPerGroup = new MenuItem(format.displayName);
                    itemPerGroup.setOnAction(e -> FXHelper.exportFile(format, ExportTask.Mode.PER_GROUP));
                    menuExportPerGroup.getItems().add(itemPerGroup);
                }
            }
            if(category != DrawingExportHandler.Category.values()[DrawingExportHandler.Category.values().length-1]){
                menuExport.getItems().add(new SeparatorMenuItem());
                menuExportPerPen.getItems().add(new SeparatorMenuItem());
                menuExportPerGroup.getItems().add(new SeparatorMenuItem());
            }
        }

        menuExport.disableProperty().bind(DrawingBotV3.INSTANCE.activeTask.isNull());
        menuFile.getItems().add(menuExport);

        menuExportPerPen.disableProperty().bind(DrawingBotV3.INSTANCE.activeTask.isNull());
        menuFile.getItems().add(menuExportPerPen);

        menuExportPerGroup.disableProperty().bind(DrawingBotV3.INSTANCE.activeTask.isNull());
        menuFile.getItems().add(menuExportPerGroup);

        menuFile.getItems().add(new SeparatorMenuItem());

        Hooks.runHook(Hooks.FILE_MENU, menuFile);

        MenuItem menuExportToVPype = new MenuItem("Export to " + VpypeHelper.VPYPE_NAME);
        menuExportToVPype.setOnAction(e -> {
            if(DrawingBotV3.INSTANCE.getActiveTask() != null){
                vpypeSettingsStage.show();
            }
        });
        menuExportToVPype.disableProperty().bind(DrawingBotV3.INSTANCE.activeTask.isNull());

        menuFile.getItems().add(menuExportToVPype);

        menuFile.getItems().add(new SeparatorMenuItem());

        MenuItem menuExportSettings = new MenuItem("Export Settings");
        menuExportSettings.setOnAction(e -> exportSettingsStage.show());
        menuFile.getItems().add(menuExportSettings);

        menuFile.getItems().add(new SeparatorMenuItem());

        MenuItem taskMonitor = new MenuItem("Open Task Monitor");
        taskMonitor.setOnAction(e -> taskMonitorStage.show());
        menuFile.getItems().add(taskMonitor);

        menuFile.getItems().add(new SeparatorMenuItem());

        MenuItem menuQuit = new MenuItem("Quit");
        menuQuit.setOnAction(e -> Platform.exit());
        menuFile.getItems().add(menuQuit);

        //view
        ArrayList<TitledPane> allPanes = new ArrayList<>();
        for(Node node : vBoxSettings.getChildren()){
            if(node instanceof TitledPane){
                allPanes.add((TitledPane) node);
            }
        }
        for(TitledPane pane : allPanes){
            MenuItem viewButton = new MenuItem(pane.getText());
            viewButton.setOnAction(e -> {
                Platform.runLater(() -> allPanes.forEach(p -> p.expandedProperty().setValue(p == pane)));
            });
            menuView.getItems().add(viewButton);
            Button undock = new Button("", new Glyph("FontAwesome", FontAwesome.Glyph.LINK));

            undock.setOnAction(e -> {
                Stage currentStage = settingsStages.get(pane);
                if(currentStage == null){

                    //create the stage
                    Stage settingsStage = new Stage(StageStyle.DECORATED);
                    settingsStage.initModality(Modality.NONE);
                    settingsStage.initOwner(FXApplication.primaryStage);
                    settingsStage.setTitle(pane.getText());
                    settingsStage.setResizable(false);

                    //create the root node
                    ScrollPane scrollPane = new ScrollPane();
                    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
                    scrollPane.setPrefWidth(420);

                    //transfer the content
                    Node content = pane.getContent();
                    pane.setAnimated(false);
                    pane.setExpanded(true);
                    pane.layout();

                    pane.setContent(new AnchorPane());
                    scrollPane.setContent(content);

                    pane.setExpanded(false);
                    pane.setAnimated(true);

                    //save the reference for later
                    settingsStages.put(pane, settingsStage);
                    settingsContent.put(pane, content);


                    //show the scene
                    Scene scene = new Scene(scrollPane);
                    settingsStage.setScene(scene);
                    settingsStage.setOnCloseRequest(event -> redockSettingsPane(pane));
                    FXApplication.applyDBStyle(settingsStage);
                    settingsStage.show();
                }else{
                    redockSettingsPane(pane);
                    currentStage.close();
                }
            });

            pane.setContentDisplay(ContentDisplay.RIGHT);
            pane.setGraphic(undock);
            undock.translateXProperty().bind(Bindings.createDoubleBinding(
                    () -> pane.getWidth() - undock.getLayoutX() - undock.getWidth() - 30,
                    pane.widthProperty())
            );
        }



        //filters
        for(Map.Entry<EnumFilterTypes, ObservableList<GenericFactory<BufferedImageOp>>> entry : MasterRegistry.INSTANCE.imgFilterFactories.entrySet()){
            Menu type = new Menu(entry.getKey().toString());

            for(GenericFactory<BufferedImageOp> factory : entry.getValue()){
                MenuItem item = new MenuItem(factory.getName());
                item.setOnAction(e -> FXHelper.addImageFilter(factory));
                type.getItems().add(item);
            }

            menuFilters.getItems().add(type);
        }

        //help
        if(!FXApplication.isPremiumEnabled){
            MenuItem upgrade = new MenuItem("Upgrade");
            upgrade.setOnAction(e -> FXHelper.openURL(DBConstants.URL_UPGRADE));
            menuHelp.getItems().add(upgrade);
            menuHelp.getItems().add(new SeparatorMenuItem());
        }

        MenuItem documentation = new MenuItem("View Documentation");
        documentation.setOnAction(e -> FXHelper.openURL(DBConstants.URL_READ_THE_DOCS_HOME));
        menuHelp.getItems().add(documentation);

        MenuItem sourceCode = new MenuItem("View Source Code");
        sourceCode.setOnAction(e -> FXHelper.openURL(DBConstants.URL_GITHUB_REPO));
        menuHelp.getItems().add(sourceCode);

        MenuItem configFolder = new MenuItem("Open Configs Folder");
        configFolder.setOnAction(e -> FXHelper.openFolder(new File(FileUtils.getUserDataDirectory())));
        menuHelp.getItems().add(configFolder);
    }

    public void redockSettingsPane(TitledPane pane){
        Node content = settingsContent.get(pane);
        pane.setContent(content);

        settingsStages.put(pane, null);
        settingsContent.put(pane, null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //// VIEWPORT PANE

    ////VIEWPORT WINDOW
    public VBox vBoxViewportContainer = null;
    public ZoomableScrollPane viewportScrollPane = null;

    ////VIEWPORT SETTINGS
    public RangeSlider rangeSliderDisplayedLines = null;
    public TextField textFieldDisplayedShapesMin = null;
    public TextField textFieldDisplayedShapesMax = null;

    public CheckBox checkBoxApplyToExport = null;

    public ChoiceBox<IDisplayMode> choiceBoxDisplayMode = null;
    public ComboBox<EnumBlendMode> comboBoxBlendMode = null;
    public Button buttonResetView = null;

    ////PLOT DETAILS
    public Label labelElapsedTime = null;
    public Label labelPlottedShapes = null;
    public Label labelPlottedVertices = null;
    public Label labelImageResolution = null;
    public Label labelPlottingResolution = null;
    public Label labelCurrentPosition = null;

    public Rectangle colourPickerRectangle;

    public CheckBox checkBoxDarkTheme = null;

    public void initViewport(){

        ////VIEWPORT SETTINGS
        rangeSliderDisplayedLines.highValueProperty().addListener((observable, oldValue, newValue) -> {
            PlottingTask task = DrawingBotV3.INSTANCE.getActiveTask();
            if(task != null){
                int lines = (int)Utils.mapDouble(newValue.doubleValue(), 0, 1, 0, task.plottedDrawing.getGeometryCount());
                task.plottedDrawing.displayedShapeMax.setValue(lines);
                textFieldDisplayedShapesMax.setText(String.valueOf(lines));
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        rangeSliderDisplayedLines.lowValueProperty().addListener((observable, oldValue, newValue) -> {
            PlottingTask task = DrawingBotV3.INSTANCE.getActiveTask();
            if(task != null){
                int lines = (int)Utils.mapDouble(newValue.doubleValue(), 0, 1, 0, task.plottedDrawing.getGeometryCount());
                task.plottedDrawing.displayedShapeMin.setValue(lines);
                textFieldDisplayedShapesMin.setText(String.valueOf(lines));
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        textFieldDisplayedShapesMax.setOnAction(e -> {
            PlottingTask task = DrawingBotV3.INSTANCE.getActiveTask();
            if(task != null){
                int lines = (int)Math.max(0, Math.min(task.plottedDrawing.getGeometryCount(), Double.parseDouble(textFieldDisplayedShapesMax.getText())));
                task.plottedDrawing.displayedShapeMax.setValue(lines);
                textFieldDisplayedShapesMax.setText(String.valueOf(lines));
                rangeSliderDisplayedLines.setHighValue((double)lines / task.plottedDrawing.getGeometryCount());
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        textFieldDisplayedShapesMin.setOnAction(e -> {
            PlottingTask task = DrawingBotV3.INSTANCE.getActiveTask();
            if(task != null){
                int lines = (int)Math.max(0, Math.min(task.plottedDrawing.getGeometryCount(), Double.parseDouble(textFieldDisplayedShapesMin.getText())));
                task.plottedDrawing.displayedShapeMin.setValue(lines);
                textFieldDisplayedShapesMin.setText(String.valueOf(lines));
                rangeSliderDisplayedLines.setLowValue((double)lines / task.plottedDrawing.getGeometryCount());
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        checkBoxApplyToExport.selectedProperty().bindBidirectional(DrawingBotV3.INSTANCE.exportRange);

        choiceBoxDisplayMode.getItems().addAll(MasterRegistry.INSTANCE.displayModes);
        choiceBoxDisplayMode.valueProperty().bindBidirectional(DrawingBotV3.INSTANCE.displayMode);

        comboBoxBlendMode.setItems(FXCollections.observableArrayList(EnumBlendMode.values()));
        comboBoxBlendMode.valueProperty().bindBidirectional(DrawingBotV3.INSTANCE.blendMode);

        buttonResetView.setOnAction(e -> DrawingBotV3.INSTANCE.resetView());

        checkBoxDarkTheme.setSelected(ConfigFileHandler.getApplicationSettings().darkTheme);
        checkBoxDarkTheme.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            ConfigFileHandler.getApplicationSettings().darkTheme = isSelected;
            ConfigFileHandler.getApplicationSettings().markDirty();
            FXApplication.applyCurrentTheme();
        });


        //DrawingBotV3.INSTANCE.displayGrid.bind(checkBoxShowGrid.selectedProperty());
        //DrawingBotV3.INSTANCE.displayGrid.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.reRender());

        viewportScrollPane.setOnDragOver(event -> {

            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.LINK);
            }
            event.consume();
        });

        viewportScrollPane.setOnDragDropped(event -> {

            Dragboard db = event.getDragboard();
            boolean success = false;
            if(db.hasFiles()){
                List<File> files = db.getFiles();
                DrawingBotV3.INSTANCE.openFile(files.get(0), false);
                success = true;
            }
            event.setDropCompleted(success);

            event.consume();
        });

        labelElapsedTime.setText("0 s");
        labelPlottedShapes.setText("0");
        labelPlottedVertices.setText("0");
        labelImageResolution.setText("0 x 0");
        labelPlottingResolution.setText("0 x 0");
        labelCurrentPosition.setText("0 x 0 y");
    }

    private final WritableImage snapshotImage = new WritableImage(1, 1);
    private ObservableDrawingPen penForColourPicker;
    private boolean colourPickerActive;

    public void startColourPick(ObservableDrawingPen pen){
        penForColourPicker = pen;
        colourPickerActive = true;
        viewportScrollPane.setCursor(Cursor.CROSSHAIR);
        colourPickerRectangle.setVisible(true);
        colourPickerRectangle.setFill(pen.javaFXColour.get());
    }

    public void doColourPick(MouseEvent event, boolean update){
        Point2D localPoint = viewportScrollPane.getParent().sceneToLocal(event.getSceneX(), event.getSceneY());

        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setViewport(new Rectangle2D(localPoint.getX(), localPoint.getY(), 1, 1));

        viewportScrollPane.snapshot((result) -> setPickResult(result, update), parameters, snapshotImage);
    }

    public Void setPickResult(SnapshotResult result, boolean update){
        Color color = result.getImage().getPixelReader().getColor(0, 0);
        if(update){
            colourPickerRectangle.setFill(color);
        }else{
            penForColourPicker.javaFXColour.set(color);
            endColourPick();
        }
        return null;
    }

    public void endColourPick(){
        viewportScrollPane.setCursor(Cursor.DEFAULT);
        penForColourPicker = null;
        colourPickerActive = false;
        colourPickerRectangle.setVisible(false);
    }

    public void onMouseMovedColourPicker(MouseEvent event){
        if(colourPickerActive){
            doColourPick(event, true);
        }
    }

    public void onMousePressedColourPicker(MouseEvent event){
        if(colourPickerActive && event.isSecondaryButtonDown()){
            doColourPick(event, false);
            event.consume();
        }
    }

    public void onKeyPressedColourPicker(KeyEvent event){
        if(event.getCode() == KeyCode.ESCAPE){
            endColourPick();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    //// PLOTTING CONTROLS

    public Button buttonStartPlotting = null;
    public Button buttonStopPlotting = null;
    public Button buttonResetPlotting = null;
    public Button buttonSaveVersion = null;

    public void initPlottingControls(){
        buttonStartPlotting.setOnAction(param -> DrawingBotV3.INSTANCE.startPlotting());
        buttonStartPlotting.disableProperty().bind(DrawingBotV3.INSTANCE.taskMonitor.isPlotting);
        buttonStopPlotting.setOnAction(param -> DrawingBotV3.INSTANCE.stopPlotting());
        buttonStopPlotting.disableProperty().bind(DrawingBotV3.INSTANCE.taskMonitor.isPlotting.not());
        buttonResetPlotting.setOnAction(param -> DrawingBotV3.INSTANCE.resetPlotting());

        buttonSaveVersion.setOnAction(param -> DrawingBotV3.INSTANCE.saveVersion());
        buttonSaveVersion.disableProperty().bind(Bindings.createBooleanBinding(() -> DrawingBotV3.INSTANCE.taskMonitor.isPlotting.get() || DrawingBotV3.INSTANCE.activeTask.get() == null, DrawingBotV3.INSTANCE.taskMonitor.isPlotting, DrawingBotV3.INSTANCE.activeTask));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    ////PROGRESS BAR PANE

    public Pane paneProgressBar = null;
    public ProgressBar progressBarGeneral = null;
    public Label progressBarLabel = null;
    public Label labelCancelExport = null;
    public Label labelOpenDestinationFolder = null;

    public void initProgressBar(){
        progressBarGeneral.prefWidthProperty().bind(paneProgressBar.widthProperty());
        progressBarLabel.setText("");

        progressBarGeneral.progressProperty().bind(DrawingBotV3.INSTANCE.taskMonitor.progressProperty);
        progressBarLabel.textProperty().bind(Bindings.createStringBinding(() -> DrawingBotV3.INSTANCE.taskMonitor.getCurrentTaskStatus(), DrawingBotV3.INSTANCE.taskMonitor.messageProperty, DrawingBotV3.INSTANCE.taskMonitor.titleProperty, DrawingBotV3.INSTANCE.taskMonitor.exceptionProperty));

        labelCancelExport.setOnMouseEntered(event -> labelCancelExport.textFillProperty().setValue(Color.BLANCHEDALMOND));
        labelCancelExport.setOnMouseExited(event -> labelCancelExport.textFillProperty().setValue(Color.BLACK));
        labelCancelExport.setOnMouseClicked(event -> {
            Task<?> task = DrawingBotV3.INSTANCE.taskMonitor.currentTask;
            if(task instanceof ExportTask){
                DrawingBotV3.INSTANCE.resetTaskService();
            }
        });
        labelCancelExport.visibleProperty().bind(DrawingBotV3.INSTANCE.taskMonitor.isExporting);

        labelOpenDestinationFolder.setOnMouseEntered(event -> labelOpenDestinationFolder.textFillProperty().setValue(Color.BLANCHEDALMOND));
        labelOpenDestinationFolder.setOnMouseExited(event -> labelOpenDestinationFolder.textFillProperty().setValue(Color.BLACK));
        labelOpenDestinationFolder.setOnMouseClicked(event -> {
            ExportTask task = DrawingBotV3.INSTANCE.taskMonitor.getDisplayedExportTask();
            if(task != null){
                FXHelper.openFolder(task.saveLocation.getParentFile());
            }
        });
        labelOpenDestinationFolder.visibleProperty().bind(Bindings.createBooleanBinding(() -> DrawingBotV3.INSTANCE.taskMonitor.wasExporting.get() || DrawingBotV3.INSTANCE.taskMonitor.isExporting.get(), DrawingBotV3.INSTANCE.taskMonitor.isExporting, DrawingBotV3.INSTANCE.taskMonitor.wasExporting));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////DRAWING AREA PANE
    public ComboBox<GenericPreset<PresetDrawingArea>> comboBoxDrawingAreaPreset = null;
    public MenuButton menuButtonDrawingAreaPresets = null;

    /////SIZING OPTIONS
    public CheckBox checkBoxOriginalSizing = null;
    public ChoiceBox<UnitsLength> choiceBoxDrawingUnits = null;
    public Pane paneDrawingAreaCustom = null;
    public TextField textFieldDrawingWidth = null;
    public TextField textFieldDrawingHeight = null;
    public Button buttonRotate = null;
    public TextField textFieldPaddingLeft = null;
    public TextField textFieldPaddingRight = null;
    public TextField textFieldPaddingTop = null;
    public TextField textFieldPaddingBottom = null;
    public CheckBox checkBoxGangPadding = null;

    public ChoiceBox<EnumScalingMode> choiceBoxScalingMode = null;

    public CheckBox checkBoxOptimiseForPrint = null;
    public TextField textFieldPenWidth = null;

    public ColorPicker colorPickerCanvas = null;

    public void initDrawingAreaPane(){

        colorPickerCanvas.setValue(Color.WHITE);
        colorPickerCanvas.valueProperty().bindBidirectional(DrawingBotV3.INSTANCE.canvasColor);

        comboBoxDrawingAreaPreset.setItems(Register.PRESET_LOADER_DRAWING_AREA.presets);
        comboBoxDrawingAreaPreset.setValue(Register.PRESET_LOADER_DRAWING_AREA.getDefaultPreset());
        comboBoxDrawingAreaPreset.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null){
                Register.PRESET_LOADER_DRAWING_AREA.applyPreset(newValue);
            }
        });

        FXHelper.setupPresetMenuButton(Register.PRESET_LOADER_DRAWING_AREA, menuButtonDrawingAreaPresets, false, comboBoxDrawingAreaPreset::getValue, (preset) -> {
            comboBoxDrawingAreaPreset.setValue(preset);

            ///force update rendering
            comboBoxDrawingAreaPreset.setItems(Register.PRESET_LOADER_DRAWING_AREA.presets);
            comboBoxDrawingAreaPreset.setButtonCell(new ComboBoxListCell<>());
        });


        /////SIZING OPTIONS
        checkBoxOriginalSizing.selectedProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.useOriginalSizing);

        paneDrawingAreaCustom.disableProperty().bind(checkBoxOriginalSizing.selectedProperty());
        choiceBoxDrawingUnits.disableProperty().bind(checkBoxOriginalSizing.selectedProperty());

        choiceBoxDrawingUnits.getItems().addAll(UnitsLength.values());
        choiceBoxDrawingUnits.setValue(UnitsLength.MILLIMETRES);
        choiceBoxDrawingUnits.valueProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.inputUnits);

        String drawingWidthHeightDecimalFormat = "####.###";
        textFieldDrawingWidth.textFormatterProperty().setValue(new TextFormatter<>(new FloatStringConverter(), 0F));
        textFieldDrawingWidth.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaWidth, new NumberStringConverter(new DecimalFormat(drawingWidthHeightDecimalFormat)));

        textFieldDrawingHeight.textFormatterProperty().setValue(new TextFormatter<>(new FloatStringConverter(), 0F));
        textFieldDrawingHeight.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaHeight, new NumberStringConverter(new DecimalFormat(drawingWidthHeightDecimalFormat)));

        buttonRotate.setOnAction(e -> {
            String width = textFieldDrawingWidth.getText();
            String height = textFieldDrawingHeight.getText();
            textFieldDrawingWidth.setText(height);
            textFieldDrawingHeight.setText(width);
        });

        textFieldPaddingLeft.textFormatterProperty().setValue(new TextFormatter<>(new FloatStringConverter(), 0F));
        textFieldPaddingLeft.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingLeft, new NumberStringConverter());

        textFieldPaddingRight.textFormatterProperty().setValue(new TextFormatter<>(new FloatStringConverter(), 0F));
        textFieldPaddingRight.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingRight, new NumberStringConverter());

        textFieldPaddingTop.textFormatterProperty().setValue(new TextFormatter<>(new FloatStringConverter(), 0F));
        textFieldPaddingTop.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingTop, new NumberStringConverter());

        textFieldPaddingBottom.textFormatterProperty().setValue(new TextFormatter<>(new FloatStringConverter(), 0F));
        textFieldPaddingBottom.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingBottom, new NumberStringConverter());

        checkBoxGangPadding.setSelected(true);
        checkBoxGangPadding.selectedProperty().addListener((observable, oldValue, newValue) -> updatePaddingBindings(newValue));
        updatePaddingBindings(checkBoxGangPadding.isSelected());

        choiceBoxScalingMode.getItems().addAll(EnumScalingMode.values());
        choiceBoxScalingMode.setValue(EnumScalingMode.CROP_TO_FIT);
        choiceBoxScalingMode.valueProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.scalingMode);

        checkBoxOptimiseForPrint.selectedProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.optimiseForPrint);

        textFieldPenWidth.textFormatterProperty().setValue(new TextFormatter<>(new FloatStringConverter(), 0.3F));
        textFieldPenWidth.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.targetPenWidth, new NumberStringConverter());
        textFieldPenWidth.disableProperty().bind(checkBoxOptimiseForPrint.selectedProperty().not());


        ///generic listeners
        DrawingBotV3.INSTANCE.drawingArea.useOriginalSizing.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.scalingMode.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.inputUnits.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.drawingAreaHeight.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.drawingAreaWidth.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingLeft.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingRight.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingTop.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingBottom.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.optimiseForPrint.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.drawingArea.targetPenWidth.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());

        DrawingBotV3.INSTANCE.canvasColor.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.reRender());

    }

    public void updatePaddingBindings(boolean ganged){
        if(ganged){
            DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingGang.set("0");
            textFieldPaddingLeft.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingGang);
            textFieldPaddingRight.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingGang);
            textFieldPaddingTop.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingGang);
            textFieldPaddingBottom.textProperty().bindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingGang);
        }else{
            textFieldPaddingLeft.textProperty().unbindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingGang);
            textFieldPaddingRight.textProperty().unbindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingGang);
            textFieldPaddingTop.textProperty().unbindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingGang);
            textFieldPaddingBottom.textProperty().unbindBidirectional(DrawingBotV3.INSTANCE.drawingArea.drawingAreaPaddingGang);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////PRE PROCESSING PANE

    public ComboBox<GenericPreset<PresetImageFilters>> comboBoxImageFilterPreset = null;

    public MenuButton menuButtonFilterPresets = null;

    public TableView<ObservableImageFilter> tableViewImageFilters = null;
    public TableColumn<ObservableImageFilter, Boolean> columnEnableImageFilter = null;
    public TableColumn<ObservableImageFilter, String> columnImageFilterType = null;
    public TableColumn<ObservableImageFilter, String> columnImageFilterSettings = null;

    public ComboBox<EnumFilterTypes> comboBoxFilterType = null;
    public ComboBox<GenericFactory<BufferedImageOp>> comboBoxImageFilter = null;
    public Button buttonAddFilter = null;

    public ChoiceBox<EnumRotation> choiceBoxRotation = null;
    public CheckBox checkBoxFlipX = null;
    public CheckBox checkBoxFlipY = null;

    public void initPreProcessingPane(){
        comboBoxImageFilterPreset.setItems(Register.PRESET_LOADER_FILTERS.presets);
        comboBoxImageFilterPreset.setValue(Register.PRESET_LOADER_FILTERS.getDefaultPreset());
        comboBoxImageFilterPreset.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null){
                Register.PRESET_LOADER_FILTERS.applyPreset(newValue);
            }
        });

        FXHelper.setupPresetMenuButton(Register.PRESET_LOADER_FILTERS, menuButtonFilterPresets, false, comboBoxImageFilterPreset::getValue, (preset) -> {
            comboBoxImageFilterPreset.setValue(preset);

            ///force update rendering
            comboBoxImageFilterPreset.setItems(Register.PRESET_LOADER_FILTERS.presets);
            comboBoxImageFilterPreset.setButtonCell(new ComboBoxListCell<>());
        });

        tableViewImageFilters.setItems(DrawingBotV3.INSTANCE.currentFilters);
        tableViewImageFilters.setRowFactory(param -> {
            TableRow<ObservableImageFilter> row = new TableRow<>();
            row.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if(row.getItem() == null){
                    event.consume();
                }
            });
            row.setOnMouseClicked(e -> {
                if(e.getClickCount() > 1){
                    FXHelper.openImageFilterDialog(row.getItem());
                }
            });
            row.setContextMenu(new ContextMenuObservableFilter(row));
            row.setPrefHeight(30);
            return row;
        });

        columnEnableImageFilter.setCellFactory(param -> new CheckBoxTableCell<>(index -> columnEnableImageFilter.getCellObservableValue(index)));
        columnEnableImageFilter.setCellValueFactory(param -> param.getValue().enable);

        columnImageFilterType.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        columnImageFilterType.setCellValueFactory(param -> param.getValue().name);


        columnImageFilterSettings.setCellValueFactory(param -> param.getValue().settingsString);

        comboBoxFilterType.setItems(FXCollections.observableArrayList(MasterRegistry.INSTANCE.imgFilterFactories.keySet()));
        comboBoxFilterType.setValue(MasterRegistry.INSTANCE.getDefaultImageFilterType());
        comboBoxFilterType.valueProperty().addListener((observable, oldValue, newValue) -> {
            comboBoxImageFilter.setItems(MasterRegistry.INSTANCE.imgFilterFactories.get(newValue));
            comboBoxImageFilter.setValue(MasterRegistry.INSTANCE.getDefaultImageFilter(newValue));
        });

        comboBoxImageFilter.setItems(MasterRegistry.INSTANCE.imgFilterFactories.get(MasterRegistry.INSTANCE.getDefaultImageFilterType()));
        comboBoxImageFilter.setValue(MasterRegistry.INSTANCE.getDefaultImageFilter(MasterRegistry.INSTANCE.getDefaultImageFilterType()));
        buttonAddFilter.setOnAction(e -> {
            if(comboBoxImageFilter.getValue() != null){
                FXHelper.addImageFilter(comboBoxImageFilter.getValue());
            }
        });

        choiceBoxRotation.setItems(FXCollections.observableArrayList(EnumRotation.DEFAULTS));
        choiceBoxRotation.setValue(EnumRotation.R0);
        choiceBoxRotation.valueProperty().bindBidirectional(DrawingBotV3.INSTANCE.imageRotation);

        checkBoxFlipX.setSelected(false);
        checkBoxFlipX.selectedProperty().bindBidirectional(DrawingBotV3.INSTANCE.imageFlipHorizontal);

        checkBoxFlipY.setSelected(false);
        checkBoxFlipY.selectedProperty().bindBidirectional(DrawingBotV3.INSTANCE.imageFlipVertical);

        DrawingBotV3.INSTANCE.imageRotation.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.imageFlipHorizontal.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
        DrawingBotV3.INSTANCE.imageFlipVertical.addListener((observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingAreaChanged());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////PATH FINDING CONTROLS
    public ComboBox<PFMFactory<?>> comboBoxPFM = null;

    public ComboBox<GenericPreset<PresetPFMSettings>> comboBoxPFMPreset = null;
    public MenuButton menuButtonPFMPresets = null;

    public TableView<GenericSetting<?,?>> tableViewAdvancedPFMSettings = null;
    public TableColumn<GenericSetting<?, ?>, Boolean> tableColumnLock = null;
    public TableColumn<GenericSetting<?, ?>, String> tableColumnSetting = null;
    public TableColumn<GenericSetting<?, ?>, Object> tableColumnValue = null;
    public TableColumn<GenericSetting<?, ?>, Object> tableColumnControl = null;

    public Button buttonPFMSettingReset = null;
    public Button buttonPFMSettingRandom = null;
    public Button buttonPFMSettingHelp = null;

    public void initPFMControls(){

        ////PATH FINDING CONTROLS
        DrawingBotV3.INSTANCE.pfmFactory.bindBidirectional(comboBoxPFM.valueProperty());
        comboBoxPFM.setCellFactory(param -> new ComboCellNamedSetting<>());
        comboBoxPFM.setItems(MasterRegistry.INSTANCE.getObservablePFMLoaderList());
        comboBoxPFM.setValue(MasterRegistry.INSTANCE.getDefaultPFM());
        comboBoxPFM.valueProperty().addListener((observable, oldValue, newValue) -> changePathFinderModule(newValue));


        comboBoxPFMPreset.setItems(MasterRegistry.INSTANCE.getObservablePFMPresetList());
        comboBoxPFMPreset.setValue(Register.PRESET_LOADER_PFM.getDefaultPreset());
        comboBoxPFMPreset.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null){
                Register.PRESET_LOADER_PFM.applyPreset(newValue);
            }
        });

        FXHelper.setupPresetMenuButton(Register.PRESET_LOADER_PFM, menuButtonPFMPresets, false, comboBoxPFMPreset::getValue, (preset) -> {
            comboBoxPFMPreset.setValue(preset);

            ///force update rendering
            comboBoxPFMPreset.setItems(MasterRegistry.INSTANCE.getObservablePFMPresetList());
            comboBoxPFMPreset.setButtonCell(new ComboBoxListCell<>());
        });

        DrawingBotV3.INSTANCE.pfmFactory.addListener((observable, oldValue, newValue) -> {
            comboBoxPFMPreset.setItems(MasterRegistry.INSTANCE.getObservablePFMPresetList(newValue));
            comboBoxPFMPreset.setValue(Register.PRESET_LOADER_PFM.getDefaultPresetForSubType(newValue.getName()));
        });

        tableViewAdvancedPFMSettings.setItems(MasterRegistry.INSTANCE.getObservablePFMSettingsList());
        tableViewAdvancedPFMSettings.setRowFactory(param -> {
            TableRow<GenericSetting<?, ?>> row = new TableRow<>();
            row.setContextMenu(new ContextMenuPFMSetting(row));
            return row;
        });
        DrawingBotV3.INSTANCE.pfmFactory.addListener((observable, oldValue, newValue) -> tableViewAdvancedPFMSettings.setItems(MasterRegistry.INSTANCE.getObservablePFMSettingsList()));

        tableColumnLock.setCellFactory(param -> new CheckBoxTableCell<>(index -> tableColumnLock.getCellObservableValue(index)));
        tableColumnLock.setCellValueFactory(param -> param.getValue().randomiseExclude);

        tableColumnSetting.setCellValueFactory(param -> param.getValue().key);

        tableColumnValue.setCellFactory(param -> {
            TextFieldTableCell<GenericSetting<?, ?>, Object> cell = new TextFieldTableCell<>();
            cell.setConverter(new StringConverterGenericSetting(() -> cell.tableViewProperty().get().getItems().get(cell.getIndex())));
            return cell;
        });
        tableColumnValue.setCellValueFactory(param -> (ObservableValue<Object>)param.getValue().value);

        tableColumnControl.setCellFactory(param -> new TableCellSettingControl());
        tableColumnControl.setCellValueFactory(param -> (ObservableValue<Object>)param.getValue().value);

        buttonPFMSettingReset.setOnAction(e -> Register.PRESET_LOADER_PFM.applyPreset(comboBoxPFMPreset.getValue()));

        buttonPFMSettingRandom.setOnAction(e -> GenericSetting.randomiseSettings(tableViewAdvancedPFMSettings.getItems()));
        buttonPFMSettingHelp.setOnAction(e -> FXHelper.openURL(DBConstants.URL_READ_THE_DOCS_PFMS));
    }

    public static void changeColourSplitter(ColourSeperationHandler oldValue, ColourSeperationHandler newValue){
        if(oldValue == newValue){
            return;
        }
        if(oldValue.wasApplied()){
            oldValue.resetSettings();
            oldValue.setApplied(false);
        }

        if(newValue.onUserSelected()){
            newValue.applySettings();
            newValue.setApplied(true);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////PEN SETTINGS

    public ComboBox<String> comboBoxSetType = null;
    public ComboBox<IDrawingSet<IDrawingPen>> comboBoxDrawingSet = null;
    public MenuButton menuButtonDrawingSetPresets = null;

    public TableView<ObservableDrawingPen> penTableView = null;
    public TableColumn<ObservableDrawingPen, Boolean> penEnableColumn = null;
    public TableColumn<ObservableDrawingPen, String> penTypeColumn = null;
    public TableColumn<ObservableDrawingPen, String> penNameColumn = null;
    public TableColumn<ObservableDrawingPen, Color> penColourColumn = null;
    public TableColumn<ObservableDrawingPen, Float> penStrokeColumn = null;
    public TableColumn<ObservableDrawingPen, String> penPercentageColumn = null;
    public TableColumn<ObservableDrawingPen, Integer> penWeightColumn = null;
    public TableColumn<ObservableDrawingPen, Integer> penLinesColumn = null;

    public ComboBox<String> comboBoxPenType = null;
    public ComboBox<DrawingPen> comboBoxDrawingPen = null;
    public ComboBoxListViewSkin<DrawingPen> comboBoxDrawingPenSkin = null;
    public MenuButton menuButtonDrawingPenPresets = null;

    public Button buttonAddPen = null;
    public Button buttonRemovePen = null;
    public Button buttonDuplicatePen = null;
    public Button buttonMoveUpPen = null;
    public Button buttonMoveDownPen = null;

    public ComboBox<EnumDistributionType> comboBoxDistributionType = null;
    public ComboBox<EnumDistributionOrder> comboBoxDistributionOrder = null;

    public ComboBox<ColourSeperationHandler> comboBoxColourSeperation = null;
    public Button buttonConfigureSplitter = null;

    public ComboBox<ObservableDrawingSet> comboBoxDrawingSets = null;
    //public Button buttonAddDrawingSet = null;
    //public Button buttonDeleteDrawingSet = null;

    public TableView<ObservableDrawingSet> drawingSetTableView = null;
    public TableColumn<ObservableDrawingSet, String> drawingSetNameColumn = null;
    public TableColumn<ObservableDrawingSet, List<ObservableDrawingPen>> drawingSetPensColumn = null;
    public TableColumn<ObservableDrawingSet, EnumDistributionType> drawingSetDistributionTypeColumn = null;
    public TableColumn<ObservableDrawingSet, EnumDistributionOrder> drawingSetDistributionOrderColumn = null;
    public TableColumn<ObservableDrawingSet, ColourSeperationHandler> drawingSetColourSeperatorColumn = null;
    public TableColumn<ObservableDrawingSet, Integer> drawingSetShapesColumn = null;
    public TableColumn<ObservableDrawingSet, Integer> drawingSetPercentageColumn = null;


    public Button buttonAddDrawingSetSlot = null;
    public Button buttonRemoveDrawingSetSlot = null;
    public Button buttonDuplicateDrawingSetSlot = null;
    public Button buttonMoveUpDrawingSetSlot = null;
    public Button buttonMoveDownDrawingSetSlot = null;

    public void initPenSettingsPane(){

        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        comboBoxSetType.setItems(FXCollections.observableArrayList(MasterRegistry.INSTANCE.registeredSets.keySet()));
        comboBoxSetType.setValue(MasterRegistry.INSTANCE.getDefaultDrawingSet().getType());
        comboBoxSetType.valueProperty().addListener((observable, oldValue, newValue) -> {
            comboBoxDrawingSet.setItems(MasterRegistry.INSTANCE.registeredSets.get(newValue));
            comboBoxDrawingSet.setValue(null);
        });

        comboBoxDrawingSet.setItems(MasterRegistry.INSTANCE.registeredSets.get(comboBoxSetType.getValue()));
        comboBoxDrawingSet.setValue(MasterRegistry.INSTANCE.getDefaultDrawingSet());
        comboBoxDrawingSet.valueProperty().addListener((observable, oldValue, newValue) -> changeDrawingSet(newValue));
        comboBoxDrawingSet.setCellFactory(param -> new ComboCellDrawingSet<>());
        comboBoxDrawingSet.setButtonCell(new ComboCellDrawingSet<>());
        comboBoxDrawingSet.setPromptText("Select a Drawing Set");

        FXHelper.setupPresetMenuButton(Register.PRESET_LOADER_DRAWING_SET, menuButtonDrawingSetPresets, true,
            () -> {
            if(comboBoxDrawingSet.getValue() instanceof PresetDrawingSet){
                PresetDrawingSet set = (PresetDrawingSet) comboBoxDrawingSet.getValue();
                return set.preset;
            }
            return null;
        }, (preset) -> {
            //force update rendering
            comboBoxSetType.setItems(FXCollections.observableArrayList(MasterRegistry.INSTANCE.registeredSets.keySet()));
            comboBoxDrawingSet.setItems(MasterRegistry.INSTANCE.registeredSets.get(comboBoxSetType.getValue()));
            comboBoxDrawingSet.setButtonCell(new ComboCellDrawingSet<>());
            if(preset != null){
                comboBoxSetType.setValue(preset.presetSubType);
                comboBoxDrawingSet.setValue(preset.data);
            }
            /*
            else{
                //don't set to avoid overwriting the users configured pens
                //comboBoxSetType.setValue(DrawingRegistry.INSTANCE.getDefaultSetType());
                //comboBoxDrawingSet.setValue(DrawingRegistry.INSTANCE.getDefaultSet(comboBoxSetType.getValue()));
            }
             */
        });

        Optional<MenuItem> setAsDefaultSet = menuButtonDrawingSetPresets.getItems().stream().filter(menuItem -> menuItem.getText() != null && menuItem.getText().equals("Set As Default")).findFirst();
        setAsDefaultSet.ifPresent(menuItem -> menuItem.setOnAction(e -> {
            if (comboBoxDrawingSet.getValue() != null) {
                ConfigFileHandler.getApplicationSettings().defaultPresets.put(Register.PRESET_TYPE_DRAWING_SET.id, comboBoxDrawingSet.getValue().getCodeName());
                ConfigFileHandler.getApplicationSettings().markDirty();
            }
        }));

        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        penTableView.setRowFactory(param -> {
            TableRow<ObservableDrawingPen> row = new TableRow<>();
            row.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if(row.getItem() == null){
                    event.consume();
                }
            });
            row.setContextMenu(new ContextMenuObservablePen(row));
            return row;
        });


        penTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if(DrawingBotV3.INSTANCE.displayMode.get() == Register.INSTANCE.DISPLAY_MODE_SELECTED_PEN){
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        penNameColumn.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        penNameColumn.setCellValueFactory(param -> param.getValue().name);

        penTypeColumn.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        penTypeColumn.setCellValueFactory(param -> param.getValue().type);

        penColourColumn.setCellFactory(TableCellColorPicker::new);
        penColourColumn.setCellValueFactory(param -> param.getValue().javaFXColour);

        penStrokeColumn.setCellFactory(param -> new TextFieldTableCell<>(new FloatStringConverter()));
        penStrokeColumn.setCellValueFactory(param -> param.getValue().strokeSize.asObject());

        penEnableColumn.setCellFactory(param -> new CheckBoxTableCell<>(index -> penEnableColumn.getCellObservableValue(index)));
        penEnableColumn.setCellValueFactory(param -> param.getValue().enable);

        penPercentageColumn.setCellValueFactory(param -> param.getValue().currentPercentage);

        penWeightColumn.setCellFactory(param -> new TextFieldTableCell<>(new IntegerStringConverter()));
        penWeightColumn.setCellValueFactory(param -> param.getValue().distributionWeight.asObject());

        penLinesColumn.setCellValueFactory(param -> param.getValue().currentGeometries.asObject());

        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        comboBoxPenType.setItems(FXCollections.observableArrayList(MasterRegistry.INSTANCE.registeredPens.keySet()));
        comboBoxPenType.setValue(MasterRegistry.INSTANCE.getDefaultDrawingPen().getType());

        comboBoxPenType.valueProperty().addListener((observable, oldValue, newValue) -> {
            comboBoxDrawingPen.setItems(MasterRegistry.INSTANCE.registeredPens.get(newValue));
            comboBoxDrawingPen.setValue(MasterRegistry.INSTANCE.getDefaultPen(newValue));
        });

        comboBoxDrawingPenSkin = new ComboBoxListViewSkin<>(comboBoxDrawingPen);
        comboBoxDrawingPenSkin.hideOnClickProperty().set(false);
        comboBoxDrawingPen.setSkin(comboBoxDrawingPenSkin);

        comboBoxDrawingPen.setItems(MasterRegistry.INSTANCE.registeredPens.get(comboBoxPenType.getValue()));
        comboBoxDrawingPen.setValue(MasterRegistry.INSTANCE.getDefaultDrawingPen());
        comboBoxDrawingPen.setCellFactory(param -> new ComboCellDrawingPen(true));
        comboBoxDrawingPen.setButtonCell(new ComboCellDrawingPen(false));

        FXHelper.setupPresetMenuButton(Register.PRESET_LOADER_DRAWING_PENS, menuButtonDrawingPenPresets, true,
            () -> {
                if(comboBoxDrawingPen.getValue() instanceof PresetDrawingPen){
                    PresetDrawingPen set = (PresetDrawingPen) comboBoxDrawingPen.getValue();
                    return set.preset;
                }
                return null;
            }, (preset) -> {
                //force update rendering
                comboBoxPenType.setItems(FXCollections.observableArrayList(MasterRegistry.INSTANCE.registeredPens.keySet()));
                comboBoxDrawingPen.setItems(MasterRegistry.INSTANCE.registeredPens.get(comboBoxPenType.getValue()));
                comboBoxDrawingPen.setButtonCell(new ComboCellDrawingPen(false));

                if(preset != null){
                    comboBoxPenType.setValue(preset.presetSubType);
                    comboBoxDrawingPen.setValue(preset.data);
                }else{
                    comboBoxPenType.setValue(MasterRegistry.INSTANCE.getDefaultDrawingPen().getType());
                    comboBoxDrawingPen.setValue(MasterRegistry.INSTANCE.getDefaultDrawingPen());
                }
            });

        Optional<MenuItem> setAsDefaultPen = menuButtonDrawingPenPresets.getItems().stream().filter(menuItem -> menuItem.getText() != null && menuItem.getText().equals("Set As Default")).findFirst();
        setAsDefaultPen.ifPresent(menuItem -> menuItem.setOnAction(e -> {
            if (comboBoxDrawingPen.getValue() != null) {
                ConfigFileHandler.getApplicationSettings().defaultPresets.put(Register.PRESET_TYPE_DRAWING_PENS.id, comboBoxDrawingPen.getValue().getCodeName());
                ConfigFileHandler.getApplicationSettings().markDirty();
            }
        }));

        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        buttonAddPen.setOnAction(e -> DrawingBotV3.INSTANCE.activeDrawingSet.get().addNewPen(comboBoxDrawingPen.getValue()));
        buttonAddPen.setTooltip(new Tooltip("Add Pen"));

        buttonRemovePen.setOnAction(e -> FXHelper.deleteItem(penTableView.getSelectionModel().getSelectedItem(), DrawingBotV3.INSTANCE.activeDrawingSet.get().pens));
        buttonRemovePen.setTooltip(new Tooltip("Remove Selected Pen"));
        buttonRemovePen.disableProperty().bind(penTableView.getSelectionModel().selectedItemProperty().isNull());

        buttonDuplicatePen.setOnAction(e -> {
            ObservableDrawingPen pen = penTableView.getSelectionModel().getSelectedItem();
            if(pen != null)
                DrawingBotV3.INSTANCE.activeDrawingSet.get().addNewPen(pen);
        });
        buttonDuplicatePen.setTooltip(new Tooltip("Duplicate Selected Pen"));
        buttonDuplicatePen.disableProperty().bind(penTableView.getSelectionModel().selectedItemProperty().isNull());

        buttonMoveUpPen.setOnAction(e -> FXHelper.moveItemUp(penTableView.getSelectionModel().getSelectedItem(), DrawingBotV3.INSTANCE.activeDrawingSet.get().pens));
        buttonMoveUpPen.setTooltip(new Tooltip("Move Selected Pen Up"));
        buttonMoveUpPen.disableProperty().bind(penTableView.getSelectionModel().selectedItemProperty().isNull());

        buttonMoveDownPen.setOnAction(e -> FXHelper.moveItemDown(penTableView.getSelectionModel().getSelectedItem(), DrawingBotV3.INSTANCE.activeDrawingSet.get().pens));
        buttonMoveDownPen.setTooltip(new Tooltip("Move Selected Pen Down"));
        buttonMoveDownPen.disableProperty().bind(penTableView.getSelectionModel().selectedItemProperty().isNull());

        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        comboBoxDistributionOrder.setItems(FXCollections.observableArrayList(EnumDistributionOrder.values()));

        comboBoxDistributionType.setItems(FXCollections.observableArrayList(EnumDistributionType.values()));
        comboBoxDistributionType.setButtonCell(new ComboCellDistributionType());
        comboBoxDistributionType.setCellFactory(param -> new ComboCellDistributionType());

        comboBoxColourSeperation.setItems(MasterRegistry.INSTANCE.colourSplitterHandlers);
        comboBoxColourSeperation.setButtonCell(new ComboCellNamedSetting<>());
        comboBoxColourSeperation.setCellFactory(param -> new ComboCellNamedSetting<>());

        buttonConfigureSplitter.setOnAction(e -> DrawingBotV3.INSTANCE.activeDrawingSet.get().colourSeperator.get().onUserConfigure());


        comboBoxDrawingSets.setItems(DrawingBotV3.INSTANCE.drawingSetSlots);
        comboBoxDrawingSets.setValue(DrawingBotV3.INSTANCE.activeDrawingSet.get());
        comboBoxDrawingSets.valueProperty().bindBidirectional(DrawingBotV3.INSTANCE.activeDrawingSet);
        comboBoxDrawingSets.setCellFactory(param -> new ComboCellDrawingSet<>());
        comboBoxDrawingSets.setButtonCell(new ComboCellDrawingSet<>());



        onChangedActiveDrawingSet(null, DrawingBotV3.INSTANCE.activeDrawingSet.get());

        DrawingBotV3.INSTANCE.activeDrawingSet.addListener((observable, oldValue, newValue) -> {
            onChangedActiveDrawingSet(oldValue, newValue);
        });


        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        drawingSetTableView.setRowFactory(param -> {
            TableRow<ObservableDrawingSet> row = new TableRow<>();
            row.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if(row.getItem() == null){
                    event.consume();
                }
            });
            //row.setContextMenu(new ContextMenuObservablePen(row));
            return row;
        });

        drawingSetTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if(DrawingBotV3.INSTANCE.displayMode.get() == Register.INSTANCE.DISPLAY_MODE_SELECTED_PEN){
                DrawingBotV3.INSTANCE.reRender();
            }
        });

        drawingSetTableView.setItems(DrawingBotV3.INSTANCE.drawingSetSlots);

        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        drawingSetNameColumn.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        drawingSetNameColumn.setCellValueFactory(param -> param.getValue().name);

        drawingSetPensColumn.setCellFactory(param -> new TableCellNode<>(ComboCellDrawingSet::createPenPalette));
        drawingSetPensColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().pens));

        drawingSetDistributionTypeColumn.setCellFactory(param -> new ComboBoxTableCell<>(FXCollections.observableArrayList(EnumDistributionType.values())));
        drawingSetDistributionTypeColumn.setCellValueFactory(param -> param.getValue().distributionType);

        drawingSetDistributionOrderColumn.setCellFactory(param -> new ComboBoxTableCell<>(FXCollections.observableArrayList(EnumDistributionOrder.values())));
        drawingSetDistributionOrderColumn.setCellValueFactory(param -> param.getValue().distributionOrder);

        drawingSetColourSeperatorColumn.setCellFactory(param -> new ComboBoxTableCell<>(MasterRegistry.INSTANCE.colourSplitterHandlers));
        drawingSetColourSeperatorColumn.setCellValueFactory(param -> param.getValue().colourSeperator);


        ///////////////////////////////////////////////////////////////////////////////////////////////////////

        buttonAddDrawingSetSlot.setOnAction(e -> {
            ObservableDrawingSet drawingSet = new ObservableDrawingSet(new DrawingSet("User", "Empty", new ArrayList<>()));
            DrawingBotV3.INSTANCE.drawingSetSlots.add(drawingSet);
        });
        buttonAddDrawingSetSlot.setTooltip(new Tooltip("Add Drawing Set"));

        buttonRemoveDrawingSetSlot.setOnAction(e -> {
            if(DrawingBotV3.INSTANCE.drawingSetSlots.size() > 1){
                ObservableDrawingSet toRemove = drawingSetTableView.getSelectionModel().getSelectedItem();
                DrawingBotV3.INSTANCE.drawingSetSlots.remove(toRemove);
                if(DrawingBotV3.INSTANCE.activeDrawingSet.get() == toRemove){
                    DrawingBotV3.INSTANCE.activeDrawingSet.set(DrawingBotV3.INSTANCE.drawingSetSlots.get(0));
                }
            }
        });
        buttonRemoveDrawingSetSlot.setTooltip(new Tooltip("Remove selected Drawing Set"));
        buttonRemoveDrawingSetSlot.disableProperty().bind(drawingSetTableView.getSelectionModel().selectedItemProperty().isNull());

        buttonDuplicateDrawingSetSlot.setOnAction(e -> {
            ObservableDrawingSet drawingSet = drawingSetTableView.getSelectionModel().getSelectedItem();
            if(drawingSet != null){
                DrawingBotV3.INSTANCE.drawingSetSlots.add(new ObservableDrawingSet(drawingSet));
            }
        });
        buttonDuplicateDrawingSetSlot.setTooltip(new Tooltip("Duplicate selected Drawing Set"));
        buttonDuplicateDrawingSetSlot.disableProperty().bind(drawingSetTableView.getSelectionModel().selectedItemProperty().isNull());

        buttonMoveUpDrawingSetSlot.setOnAction(e -> FXHelper.moveItemUp(drawingSetTableView.getSelectionModel().getSelectedItem(), DrawingBotV3.INSTANCE.drawingSetSlots));
        buttonMoveUpDrawingSetSlot.setTooltip(new Tooltip("Move selected Drawing Set up"));
        buttonMoveUpDrawingSetSlot.disableProperty().bind(drawingSetTableView.getSelectionModel().selectedItemProperty().isNull());

        buttonMoveDownDrawingSetSlot.setOnAction(e -> FXHelper.moveItemDown(drawingSetTableView.getSelectionModel().getSelectedItem(), DrawingBotV3.INSTANCE.drawingSetSlots));
        buttonMoveDownDrawingSetSlot.setTooltip(new Tooltip("Move selected Drawing Set down"));
        buttonMoveDownDrawingSetSlot.disableProperty().bind(drawingSetTableView.getSelectionModel().selectedItemProperty().isNull());


    }

    public void onDrawingSetChanged(){
        //force update rendering
        comboBoxDrawingSets.setButtonCell(new ComboCellDrawingSet<>());
        comboBoxDrawingSets.setCellFactory(param -> new ComboCellDrawingSet<>());

        ObservableDrawingSet activeSet = DrawingBotV3.INSTANCE.activeDrawingSet.get();
        comboBoxDrawingSets.setItems(FXCollections.observableArrayList());
        comboBoxDrawingSets.setItems(DrawingBotV3.INSTANCE.drawingSetSlots);
        comboBoxDrawingSets.setValue(activeSet);
    }

    //// DRAWING SET LISTENERS \\\\

    private ListChangeListener<ObservableDrawingPen> penListener = null;
    private ChangeListener<EnumDistributionOrder> distributionOrderListener = null;
    private ChangeListener<EnumDistributionType> distributionTypeListener = null;
    private ChangeListener<ColourSeperationHandler> colourSeperatorListener = null;

    private void initListeners(){
        penListener = c -> DrawingBotV3.INSTANCE.onDrawingSetChanged();
        distributionOrderListener = (observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingSetChanged();
        distributionTypeListener = (observable, oldValue, newValue) -> DrawingBotV3.INSTANCE.onDrawingSetChanged();
        colourSeperatorListener = (observable, oldValue, newValue) -> FXController.changeColourSplitter(oldValue, newValue);
    }

    private void addListeners(ObservableDrawingSet newValue){
        if(penListener == null){
            initListeners();
        }
        newValue.pens.addListener(penListener);
        newValue.distributionOrder.addListener(distributionOrderListener);
        newValue.distributionType.addListener(distributionTypeListener);
        newValue.colourSeperator.addListener(colourSeperatorListener);
    }

    private void removeListeners(ObservableDrawingSet oldValue){
        if(penListener == null){
            return;
        }
        oldValue.pens.removeListener(penListener);
        oldValue.distributionOrder.removeListener(distributionOrderListener);
        oldValue.distributionType.removeListener(distributionTypeListener);
        oldValue.colourSeperator.removeListener(colourSeperatorListener);
    }

    public void onChangedActiveDrawingSet(ObservableDrawingSet oldValue, ObservableDrawingSet newValue){
        if(oldValue != null){
            removeListeners(oldValue);
            comboBoxDistributionOrder.valueProperty().unbindBidirectional(oldValue.distributionOrder);
            comboBoxDistributionType.valueProperty().unbindBidirectional(oldValue.distributionType);
            comboBoxColourSeperation.valueProperty().unbindBidirectional(oldValue.colourSeperator);
        }

        if(newValue == null){
            return; //this is probably a force render update
        }

        addListeners(newValue);
        penTableView.setItems(newValue.pens);
        comboBoxDistributionOrder.valueProperty().bindBidirectional(newValue.distributionOrder);
        comboBoxDistributionType.valueProperty().bindBidirectional(newValue.distributionType);
        comboBoxColourSeperation.valueProperty().bindBidirectional(newValue.colourSeperator);

        buttonConfigureSplitter.disableProperty().unbind();
        buttonConfigureSplitter.disableProperty().bind(Bindings.createBooleanBinding(() -> !newValue.colourSeperator.get().canUserConfigure(), newValue.colourSeperator));

    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////VERSION CONTROL

    public TableView<ObservableProjectSettings> tableViewVersions = null;
    public TableColumn<ObservableProjectSettings, ImageView> versionThumbColumn = null;
    public TableColumn<ObservableProjectSettings, String> versionNameColumn = null;
    public TableColumn<ObservableProjectSettings, String> versionDateColumn = null;

    public TableColumn<ObservableProjectSettings, String> versionPFMColumn = null;
    public TableColumn<ObservableProjectSettings, String> versionFileColumn = null;


    public Button buttonAddVersion = null;
    public Button buttonDeleteVersion = null;
    public Button buttonMoveUpVersion = null;
    public Button buttonMoveDownVersion = null;

    public void initVersionControlPane() {
        tableViewVersions.setRowFactory(param -> {
            TableRow<ObservableProjectSettings> row = new TableRow<>();
            row.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if(row.getItem() == null){
                    event.consume();
                }
            });
            row.setContextMenu(new ContextMenuObservableProjectSettings(row));
            return row;
        });

        tableViewVersions.setItems(DrawingBotV3.INSTANCE.projectVersions);

        versionThumbColumn.setCellValueFactory(param -> param.getValue().imageView);

        versionNameColumn.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        versionNameColumn.setCellValueFactory(param -> param.getValue().userDefinedName);

        versionDateColumn.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        versionDateColumn.setCellValueFactory(param -> param.getValue().date);
        versionDateColumn.setEditable(false);

        versionFileColumn.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        versionFileColumn.setCellValueFactory(param -> param.getValue().file);
        versionFileColumn.setEditable(false);

        versionPFMColumn.setCellFactory(param -> new TextFieldTableCell<>(new DefaultStringConverter()));
        versionPFMColumn.setCellValueFactory(param -> param.getValue().pfm);
        versionPFMColumn.setEditable(false);

        buttonAddVersion.setOnAction(e -> DrawingBotV3.INSTANCE.saveVersion());
        buttonDeleteVersion.setOnAction(e -> FXHelper.deleteItem(tableViewVersions.getSelectionModel().getSelectedItem(), DrawingBotV3.INSTANCE.projectVersions));
        buttonMoveUpVersion.setOnAction(e -> FXHelper.moveItemUp(tableViewVersions.getSelectionModel().getSelectedItem(), DrawingBotV3.INSTANCE.projectVersions));
        buttonMoveDownVersion.setOnAction(e -> FXHelper.moveItemDown(tableViewVersions.getSelectionModel().getSelectedItem(), DrawingBotV3.INSTANCE.projectVersions));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////
    ////BATCH PROCESSING

    public TitledPane titledPaneBatchProcessing = null;
    public AnchorPane anchorPaneBatchProcessing = null;

    public Label labelInputFolder = null;
    public Label labelOutputFolder = null;

    public Button buttonSelectInputFolder = null;
    public Button buttonSelectOutputFolder = null;
    public Button buttonStartBatchProcessing = null;
    public Button buttonStopBatchProcessing = null;

    public CheckBox checkBoxOverwrite = null;

    public TableView<?> tableViewBatchExport = null;
    public TableColumn<?, String> tableColumnFileFormat = null;
    public TableColumn<?, Boolean> tableColumnPerDrawing = null;
    public TableColumn<?, Boolean> tableColumnPerPen = null;
    public TableColumn<?, Boolean> tableColumnPerGroup = null;

    public void initBatchProcessingPane(){
        ///NOP
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////////////

    public static void showPremiumFeatureDialog(){
        DialogPremiumFeature premiumFeature = new DialogPremiumFeature();

        Optional<Boolean> upgrade = premiumFeature.showAndWait();
        if(upgrade.isPresent() && upgrade.get()){
            FXHelper.openURL(DBConstants.URL_UPGRADE);
        }
    }

    public static void changePathFinderModule(PFMFactory<?> pfm){
        if(pfm.isPremiumFeature() && !FXApplication.isPremiumEnabled){
            DrawingBotV3.INSTANCE.controller.comboBoxPFM.setValue(MasterRegistry.INSTANCE.getDefaultPFM());
            showPremiumFeatureDialog();
        }else{
            DrawingBotV3.INSTANCE.pfmFactory.set(pfm);
            DrawingBotV3.INSTANCE.nextDistributionType = pfm.getDistributionType();
        }

    }

    public static void changeDrawingSet(IDrawingSet<IDrawingPen> set){
        if(set != null){
            DrawingBotV3.INSTANCE.activeDrawingSet.get().loadDrawingSet(set);
            Hooks.runHook(Hooks.CHANGE_DRAWING_SET, set);
        }
    }

    public ObservableDrawingPen getSelectedPen(){
        return penTableView.getSelectionModel().getSelectedItem();
    }

    //// PRESET MENU BUTTON \\\\

    public static class DummyController {

        public void initialize(){
            ///NOP
        }
    }
}
