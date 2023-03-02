package stocker.dialog;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.HashMap;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import stocker.control.StockerControl;
import stocker.util.ECandleScheme;
import stocker.util.EChartColors;
import stocker.util.EChartInterval;
import stocker.util.EChartType;
import stocker.util.TextfieldIntValidatorOnFocusLost;
import stocker.view.StockerFrame;

/**
 * A dialog for managing the properties of the Stocker application.
 * 
 * @author Marc S. Schneider
 */
public class StockerPropertyDialog extends JDialog {

	private static final long serialVersionUID = 3579597637443683017L;

	private HashMap<String, JsonObject> dataProviders; // local copy for easier access
	
	/**
	 * Constructs a new property dialog.
	 * @param parent the parent window (i.e. the main frame, a {@link StockerFrame})
	 * @param control the {@link StockerControl} which is controlling this application
	 */
	public StockerPropertyDialog(StockerFrame parent, StockerControl control, JsonObject props) {
		super(parent, Dialog.ModalityType.APPLICATION_MODAL);
		this.dataProviders = new HashMap<String, JsonObject>();

		this.setTitle("Einstellungen");
		this.setPreferredSize(new Dimension(400, 400));
		this.setMinimumSize(new Dimension(390, 380));

		getContentPane().setLayout(new BorderLayout());

		JTabbedPane tabs = new JTabbedPane();
		JPanel panelData = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		tabs.addTab("Datenanbieter", panelData);
		tabs.setMnemonicAt(0, KeyEvent.VK_D);
		
		JPanel panelCharts = new JPanel(new GridBagLayout());
		tabs.addTab("Charts", panelCharts);
		tabs.setMnemonicAt(1, KeyEvent.VK_C);

		add(tabs, BorderLayout.CENTER);

		JButton btnOk = new JButton("Speichern & Schließen");
		btnOk.setMnemonic('s');
		JButton btnCancel = new JButton("Abbrechen");
		btnCancel.setMnemonic('a');
		JPanel panelBtns = new JPanel(new FlowLayout());
		panelBtns.add(btnOk);
		panelBtns.add(btnCancel);

		add(panelBtns, BorderLayout.SOUTH);

		// The Data tab
		JComboBox<String> comboProviders = new JComboBox<String>();
		JsonArray providers = props.get("DataProviders").getAsJsonArray();
		for (int i = 0; i < providers.size(); i++) {
			String name = providers.get(i).getAsJsonObject().get("name").getAsString();
			comboProviders.addItem(name);
			dataProviders.put(name, providers.get(i).getAsJsonObject());
		}

		c.insets = new Insets(5, 5, 5, 5);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		panelData.add(comboProviders, c);
		c.gridwidth = 1;

		JLabel lname = new JLabel("Name:");
		JTextField tname = new JTextField(30);
		c.gridx = 0;
		c.gridy = 1;
		panelData.add(lname, c);
		c.gridx = 1;
		c.gridy = 1;
		panelData.add(tname, c);
		JLabel lpull = new JLabel("Pull URL:");
		JTextField tpull = new JTextField(30);
		c.gridx = 0;
		c.gridy = 2;
		panelData.add(lpull, c);
		c.gridx = 1;
		c.gridy = 2;
		panelData.add(tpull, c);
		JLabel lpush = new JLabel("Push URL:");
		JTextField tpush = new JTextField(30);
		c.gridx = 0;
		c.gridy = 3;
		panelData.add(lpush, c);
		c.gridx = 1;
		c.gridy = 3;
		panelData.add(tpush, c);
		JLabel ltoken = new JLabel("Token:");
		JTextField ttoken = new JTextField(30);
		c.gridx = 0;
		c.gridy = 4;
		panelData.add(ltoken, c);
		c.gridx = 1;
		c.gridy = 4;
		panelData.add(ttoken, c);

		JButton btnAddNew = new JButton("Als neu hinzufügen");
		btnAddNew.setMnemonic('n');
		JButton btnSaveProv = new JButton("Änderungen speichern");
		btnSaveProv.setMnemonic('p');
		JButton btnDeleteProv = new JButton("Löschen");
		btnDeleteProv.setMnemonic('l');
		JPanel btnPanel = new JPanel();
		btnPanel.add(btnAddNew);
		btnPanel.add(btnSaveProv);
		btnPanel.add(btnDeleteProv);
		c.gridwidth = 2;
		c.gridx = 0;
		c.gridy = 5;
		panelData.add(btnPanel, c);
		
		JCheckBox checkOnlyUSStocks = new JCheckBox("<html>Werte von Nicht-US-Börsen (und andere<br>Werte mit Punkt im Namen) ausblenden</html>");
		if (props.get("showOnlyUSStocks").getAsBoolean()) {
			checkOnlyUSStocks.setSelected(true);
		}
		c.gridwidth = 1;
		c.gridx = 1;
		c.gridy = 6;
		panelData.add(checkOnlyUSStocks, c);

		comboProviders.addItemListener(new ItemListener() { // fill text fields when a provider is selected from the combo
			@Override
			public void itemStateChanged(ItemEvent e) {
				String name = e.getItem().toString(); // the item is a string
				JsonObject prov = dataProviders.get(name);
				tname.setText(prov.get("name").getAsString());
				tpull.setText(prov.get("pullURL").getAsString());
				tpush.setText(prov.get("pushURL").getAsString());
				ttoken.setText(prov.get("token").getAsString());
			}
		});

		btnAddNew.addActionListener(new ActionListener() { // add a new provider with the data from the text fields
			@Override
			public void actionPerformed(ActionEvent e) {
				String name = tname.getText();
				if (dataProviders.containsKey(name)) {
					JOptionPane.showMessageDialog(StockerPropertyDialog.this,
							"Bitte zuerst einen neuen, eindeutigen Namen eingeben,\num einen neuen Datenanbieter hinzuzufügen",
							"Achtung", JOptionPane.WARNING_MESSAGE);
				} else {
					JsonObject newProv = new JsonObject();
					newProv.addProperty("name", name);
					newProv.addProperty("pullURL", tpull.getText());
					newProv.addProperty("pushURL", tpush.getText());
					newProv.addProperty("token", ttoken.getText());
					dataProviders.put(name, newProv); // add to our local HashMap
					props.get("DataProviders").getAsJsonArray().add(newProv); // add to the actual properties

					// update ComboBox and set text fields based on new entry
					comboProviders.addItem(name);
					comboProviders.setSelectedItem(name); // should trigger itemStateChanged
				}
			}
		});

		btnSaveProv.addActionListener(new ActionListener() { // save the changes to the currently selected provider
			@Override
			public void actionPerformed(ActionEvent e) {
				int newProv = comboProviders.getSelectedIndex();
				JsonObject prov = props.get("DataProviders").getAsJsonArray().get(newProv).getAsJsonObject();
				boolean dataProvChanged = false; // check whether there was actually a change in order to avoid unnecessary switching
				if (!prov.get("name").getAsString().equals(tname.getText())) {
					prov.addProperty("name", tname.getText());
					dataProvChanged = true;
				}
				if (!prov.get("pullURL").getAsString().equals(tpull.getText())) {
					prov.addProperty("pullURL", tpull.getText());
					dataProvChanged = true;
				}
				if (!prov.get("pushURL").getAsString().equals(tpush.getText())) {
					prov.addProperty("pushURL", tpush.getText());
					dataProvChanged = true;
				}
				if (!prov.get("token").getAsString().equals(ttoken.getText())) {
					prov.addProperty("token", ttoken.getText());
					dataProvChanged = true;
				}
				
				if (newProv != control.getActiveDataProvider()) { // if user has not only changed sth, but also switched provider
					control.setActiveDataProvider(newProv);
					dataProvChanged = true;
				}
				if (dataProvChanged) { // switch to active provider again in order to reflect the changes
					new Thread() {
						@Override
						public void run() {
							control.switchDataProvider();
						};
					}.start();
				}
			}
		});

		btnDeleteProv.addActionListener(new ActionListener() { // delete the currently selected provider
			@Override
			public void actionPerformed(ActionEvent e) {
				props.get("DataProviders").getAsJsonArray().remove(comboProviders.getSelectedIndex());
				comboProviders.removeItemAt(comboProviders.getSelectedIndex());
			}
		});

		// The Charts tab
		c.insets = new Insets(5, 5, 5, 5);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = 1;

		JLabel lcharttype = new JLabel("Standard-Charttyp");
		JComboBox<EChartType> comboChartType = new JComboBox<EChartType>(EChartType.values());
		comboChartType.setSelectedItem(EChartType.valueOf(props.get("DefaultChartType").getAsString()));
		c.gridx = 0;
		c.gridy = 0;
		panelCharts.add(lcharttype, c);
		c.gridx = 1;
		c.gridy = 0;
		panelCharts.add(comboChartType, c);
		JLabel lchartinterval = new JLabel("Standard-Chartintervall");
		JComboBox<EChartInterval> comboChartInterval = new JComboBox<EChartInterval>(EChartInterval.values());
		comboChartInterval.setSelectedItem(EChartInterval.valueOf(props.get("DefaultChartInterval").getAsString()));
		c.gridx = 0;
		c.gridy = 1;
		panelCharts.add(lchartinterval, c);
		c.gridx = 1;
		c.gridy = 1;
		panelCharts.add(comboChartInterval, c);
		JLabel lindicatorcolor = new JLabel("Standard-Farbe für Indikatoren");
		JComboBox<EChartColors> comboChartInidicatorColors = new JComboBox<EChartColors>(EChartColors.values());
		comboChartInidicatorColors.setSelectedItem(EChartColors.valueOf(props.get("IndicatorColor").getAsString()));
		c.gridx = 0;
		c.gridy = 2;
		panelCharts.add(lindicatorcolor, c);
		c.gridx = 1;
		c.gridy = 2;
		panelCharts.add(comboChartInidicatorColors, c);
		JLabel lalarmcolor = new JLabel("Farbe für Alarme");
		JComboBox<EChartColors> comboChartAlarmColors = new JComboBox<EChartColors>(EChartColors.values());
		comboChartAlarmColors.setSelectedItem(EChartColors.valueOf(props.get("AlarmColor").getAsString()));
		c.gridx = 0;
		c.gridy = 3;
		panelCharts.add(lalarmcolor, c);
		c.gridx = 1;
		c.gridy = 3;
		panelCharts.add(comboChartAlarmColors, c);
		JLabel lcandlescheme = new JLabel("Kerzendarstellung");
		JComboBox<ECandleScheme> comboCandleScheme = new JComboBox<ECandleScheme>(ECandleScheme.values());
		comboCandleScheme.setSelectedItem(ECandleScheme.valueOf(props.get("CandleScheme").getAsString()));
		c.gridx = 0;
		c.gridy = 4;
		panelCharts.add(lcandlescheme, c);
		c.gridx = 1;
		c.gridy = 4;
		panelCharts.add(comboCandleScheme, c);

		JsonArray minSize = props.get("MinimumSize").getAsJsonArray();
		JLabel lminsizex = new JLabel("Charts Mindestbreite:");
		JTextField tMinSizeX = new JTextField(5);
		Integer oldMinSizeX = minSize.get(0).getAsInt();
		tMinSizeX.setText(oldMinSizeX.toString());
		c.gridx = 0;
		c.gridy = 5;
		panelCharts.add(lminsizex, c);
		c.gridx = 1;
		c.gridy = 5;
		panelCharts.add(tMinSizeX, c);

		JLabel lminsizey = new JLabel("Charts Mindesthöhe:");
		JTextField tMinSizeY = new JTextField(5);
		Integer oldMinSizeY = minSize.get(1).getAsInt();
		tMinSizeY.setText(oldMinSizeY.toString());
		c.gridx = 0;
		c.gridy = 6;
		panelCharts.add(lminsizey, c);
		c.gridx = 1;
		c.gridy = 6;
		panelCharts.add(tMinSizeY, c);

		tMinSizeX.addFocusListener(new TextfieldIntValidatorOnFocusLost(tMinSizeX, 580, 10000, 580));
		tMinSizeY.addFocusListener(new TextfieldIntValidatorOnFocusLost(tMinSizeY, 450, 10000, 450));

		btnOk.addActionListener(new ActionListener() { // dialog closed with confirmation button -> save all changes
			@Override
			public void actionPerformed(ActionEvent e) {
				// Save settings in Data tab - only the selected data provider
				JsonObject prov = props.get("DataProviders").getAsJsonArray().get(comboProviders.getSelectedIndex()).getAsJsonObject();
				boolean dataProvChanged = false; // check whether there was actually a change in order to avoid unnecessary switching
				if (!prov.get("name").getAsString().equals(tname.getText())) {
					prov.addProperty("name", tname.getText());
					dataProvChanged = true;
				}
				if (!prov.get("pullURL").getAsString().equals(tpull.getText())) {
					prov.addProperty("pullURL", tpull.getText());
					dataProvChanged = true;
				}
				if (!prov.get("pushURL").getAsString().equals(tpush.getText())) {
					prov.addProperty("pushURL", tpush.getText());
					dataProvChanged = true;
				}
				if (!prov.get("token").getAsString().equals(ttoken.getText())) {
					prov.addProperty("token", ttoken.getText());
					dataProvChanged = true;
				}
				int oldProv = control.getActiveDataProvider();
				int newProv = comboProviders.getSelectedIndex();
				if (oldProv != newProv || dataProvChanged) {
					control.setActiveDataProvider(newProv);
					new Thread() {
						@Override
						public void run() {
							control.switchDataProvider();
						};
					}.start();
				}
				props.addProperty("showOnlyUSStocks", checkOnlyUSStocks.isSelected());
				
				// Save settings in Chart tab
				props.addProperty("DefaultChartType", ((EChartType) comboChartType.getSelectedItem()).toObjectString());
				props.addProperty("DefaultChartInterval",
						((EChartInterval) comboChartInterval.getSelectedItem()).toObjectString());
				props.addProperty("IndicatorColor",
						((EChartColors) comboChartInidicatorColors.getSelectedItem()).toObjectString());
				props.addProperty("AlarmColor",
						((EChartColors) comboChartAlarmColors.getSelectedItem()).toObjectString());
				props.addProperty("CandleScheme",
						((ECandleScheme) comboCandleScheme.getSelectedItem()).toObjectString());

				int minSizeX = Integer.parseInt(tMinSizeX.getText()); // we have already checked for errors when losing focus
				int minSizeY = Integer.parseInt(tMinSizeY.getText());
				minSize.set(0, new JsonPrimitive(minSizeX));
				minSize.set(1, new JsonPrimitive(minSizeY));
				parent.enforceMinimumChildWindowSize();
				parent.updateChartProperties();

				setVisible(false);
				dispose();
			}
		});

		btnCancel.addActionListener(new ActionListener() { // dialog canceled -> drop all changes, just close
			@Override
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
				dispose();
			}
		});

		// initialize elements in data provider tab 
		comboProviders.setSelectedIndex(control.getActiveDataProvider()); 
		String name = providers.get(control.getActiveDataProvider()).getAsJsonObject().get("name").getAsString();
		JsonObject prov = dataProviders.get(name);
		tname.setText(prov.get("name").getAsString());
		tpull.setText(prov.get("pullURL").getAsString());
		tpush.setText(prov.get("pushURL").getAsString());
		ttoken.setText(prov.get("token").getAsString());

		pack();
		setLocationRelativeTo(parent);

	}

}
