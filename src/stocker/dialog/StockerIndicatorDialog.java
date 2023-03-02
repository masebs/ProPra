package stocker.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Iterator;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import stocker.model.ChartIndicator;
import stocker.util.EChartColors;
import stocker.util.EChartIndicator;
import stocker.view.StockerChart;

/**
 * Provides a dialog for the configuration of chart indicators.
 * 
 * @author Marc S. Schneider
 */
public class StockerIndicatorDialog extends JDialog implements ActionListener {

	private static final long serialVersionUID = -1764621622351060877L;
	private StockerChart parent;
	private JButton btnAdd, btnRemove, btnClose;
	private JList<EChartIndicator> listAdd;
	private JList<ChartIndicator> listRemove;
	private EChartColors defaultColor;

	/** 
	 * Construct a new StockerIndicatorDialog.
	 * @param parent the parent of this dialog (a {@link StockerChart})
	 */
	public StockerIndicatorDialog(StockerChart parent, EChartColors defaultColor) {
		super(SwingUtilities.windowForComponent(parent.getDesktopPane()), Dialog.ModalityType.APPLICATION_MODAL);
		this.parent = parent;
		this.defaultColor = defaultColor;

		this.setTitle("Indikatoren verwalten");
		this.setPreferredSize(new Dimension(300, 320));
		this.setMinimumSize(new Dimension(250, 250));

		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

		// Panel to add a new indicator
		JPanel panelAdd = new JPanel(new BorderLayout());
		panelAdd.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(100, 100, 100)),
				"Verfügbare Indikatortypen"));
		DefaultListModel<EChartIndicator> listAddModel = new DefaultListModel<EChartIndicator>();
		listAdd = new JList<EChartIndicator>(listAddModel);
		EChartIndicator[] vals = EChartIndicator.values();
		for (int i = 0; i < vals.length; i++) {
			listAddModel.addElement(vals[i]);
		}
		listAdd.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		listAdd.addMouseListener(new MouseAdapter() { // select by double-click
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) { // it's a double click
					btnAdd.doClick(10);
				}
			}
		});
		listAdd.addKeyListener(new KeyAdapter() { // select by space or enter key
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER) {
					btnAdd.doClick(10);
				}
			}
			
		});
		listAdd.setVisibleRowCount(4);
		panelAdd.add(new JScrollPane(listAdd), BorderLayout.CENTER);
		btnAdd = new JButton("Hinzufügen");
		btnAdd.setMnemonic(KeyEvent.VK_H);
		btnAdd.addActionListener(this);
		JPanel btnAddPanel = new JPanel(new BorderLayout());
		btnAddPanel.add(btnAdd, BorderLayout.SOUTH);
		panelAdd.add(btnAddPanel, BorderLayout.EAST);

		add(panelAdd);

		// Panel to remove an existing indicator
		JPanel panelRemove = new JPanel(new BorderLayout());
		panelRemove.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(100, 100, 100)),
				"Aktivierte Indikatoren"));
		DefaultListModel<ChartIndicator> listRemoveModel = new DefaultListModel<ChartIndicator>();
		listRemove = new JList<ChartIndicator>(listRemoveModel);
		writeRemoveList();
		listRemove.setVisibleRowCount(6);
		panelRemove.add(new JScrollPane(listRemove), BorderLayout.CENTER);
		btnRemove = new JButton("Entfernen");
		btnRemove.setMnemonic(KeyEvent.VK_E);
		btnRemove.setPreferredSize(new Dimension(100, 30));
		btnRemove.addActionListener(this);
		JPanel btnRemovePanel = new JPanel(new BorderLayout());
		btnRemovePanel.add(btnRemove, BorderLayout.SOUTH);
		panelRemove.add(btnRemovePanel, BorderLayout.EAST);

		add(panelRemove);

		JPanel panelBtn = new JPanel();
		btnClose = new JButton("Schließen");
		btnClose.setMnemonic('s');
		btnClose.addActionListener(this);
		panelBtn.add(btnClose);
		add(panelBtn);

		pack();
		setLocationRelativeTo(parent);

		setVisible(true);
	}

	/** 
	 * Writes the list of currently present indicators at the bottom of the dialog.
	 */
	private void writeRemoveList() {
		Iterator<ChartIndicator> ciIt = parent.getChartIndicators().iterator();
		DefaultListModel<ChartIndicator> listRemoveModel = (DefaultListModel<ChartIndicator>) listRemove.getModel();
		listRemoveModel.clear();
		while (ciIt.hasNext()) {
			listRemoveModel.addElement(ciIt.next());
		}
	}

	/**
	 * Action handler which reacts on any button presses.
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		JButton btn = ((JButton) e.getSource());
		if (btn == btnClose) {
			setVisible(false);
			dispose();
		} else if (btn == btnAdd) {
			// The enum kind of works like a Factory for ChartIndicators
			EChartIndicator ciToAdd = listAdd.getSelectedValue();
			if (ciToAdd == null) {
				JOptionPane.showMessageDialog(this, "Bitte einen Indikatortypen auswählen", "Typ auswählen",
						JOptionPane.WARNING_MESSAGE);
			} else {
				ChartIndicator ci = ciToAdd.getIndicator();
				ci.setColor(defaultColor);
				// get the parameters by a JOptionPane
				if (JOptionPane.showConfirmDialog(this, ci.getParametersMessage(), "Parameter angeben",
						JOptionPane.OK_CANCEL_OPTION) == 0) {
					ci.parametrizeFromTextfields();
					parent.addChartIndicator(ci); // add indicator to StockerChart
				}
				writeRemoveList(); // after adding, reload the currently present indicators
			}
		} else if (btn == btnRemove) {
			Iterator<ChartIndicator> removeIt = listRemove.getSelectedValuesList().iterator();
			while (removeIt.hasNext()) {
				parent.removeChartIndicator(removeIt.next());
			}
			writeRemoveList();
		}
	}

}
