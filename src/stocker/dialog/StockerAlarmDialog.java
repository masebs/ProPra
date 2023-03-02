package stocker.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import stocker.model.ChartAlarm;
import stocker.view.StockerChart;

/** 
 * A dialog in which the user can create and remove alarms referring to the current chart.
 * 
 * @author Marc S. Schneider
 */
public class StockerAlarmDialog extends JDialog implements ActionListener {

	private static final long serialVersionUID = 6998607775299147816L;
	private StockerChart parent;
	private JButton btnAdd, btnRemove, btnClose;
	private JTextField tAddAlarm;
	private JList<ChartAlarm> listRemove;

	/**
	 * Constructs a new StockerAlarmDialog.
	 * @param parent the parent window if this dialog (a {@link StockerChart})
	 */
	public StockerAlarmDialog(StockerChart parent) {
		super(SwingUtilities.windowForComponent(parent.getDesktopPane()), Dialog.ModalityType.APPLICATION_MODAL);
		this.parent = parent;

		this.setTitle("Alarme verwalten");
		this.setPreferredSize(new Dimension(300, 260));
		this.setMinimumSize(new Dimension(250, 220));

		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));

		// Panel to add a new alarm value
		JPanel panelAdd = new JPanel(new BorderLayout());
		panelAdd.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(100, 100, 100)),
				"Neuer Alarm"));
		tAddAlarm = new JTextField(6);
		tAddAlarm.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				btnAdd.doClick(10);
			}
		});
		panelAdd.add(tAddAlarm, BorderLayout.CENTER);
		btnAdd = new JButton("Hinzufügen");
		btnAdd.setMnemonic('h');
		btnAdd.setPreferredSize(new Dimension(100, 30));
		btnAdd.addActionListener(this);
		JPanel btnAddPanel = new JPanel(new BorderLayout());
		btnAddPanel.add(btnAdd, BorderLayout.SOUTH);
		panelAdd.add(btnAddPanel, BorderLayout.EAST);

		add(panelAdd);

		// Panel to remove an existing alarm value
		JPanel panelRemove = new JPanel(new BorderLayout());
		panelRemove.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(100, 100, 100)),
				"Aktive Alarme"));
		DefaultListModel<ChartAlarm> listRemoveModel = new DefaultListModel<ChartAlarm>();
		listRemove = new JList<ChartAlarm>(listRemoveModel);
		writeRemoveList();
		listRemove.setVisibleRowCount(6);
		panelRemove.add(new JScrollPane(listRemove), BorderLayout.CENTER);
		
		btnRemove = new JButton("Entfernen");
		btnRemove.setMnemonic('e');
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

	private void writeRemoveList() {
		Iterator<ChartAlarm> ciIt = parent.getChartAlarms().iterator();
		DefaultListModel<ChartAlarm> listRemoveModel = (DefaultListModel<ChartAlarm>) listRemove.getModel();
		listRemoveModel.clear();
		while (ciIt.hasNext()) {
			listRemoveModel.addElement(ciIt.next());
		}
	}

	/**
	 * Event listener: Handles clicks on all buttons in this dialog
	 * @param e the {@link ActionEvent} describing the action
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		JButton btn = ((JButton) e.getSource());
		if (btn == btnClose) {
			setVisible(false);
			dispose();
		} else if (btn == btnAdd) {
			try {
				double value = Double.parseDouble(tAddAlarm.getText().replace(',', '.'));
				DefaultListModel<ChartAlarm> listRemoveModel = (DefaultListModel<ChartAlarm>) listRemove.getModel();
				boolean isNew = true; // check if an alarm for this value already exists
				for (int i = 0; i < listRemoveModel.getSize(); i++) {
					if (listRemoveModel.get(i).getValue() == value) {
						isNew = false;
					}
				}
				if (isNew) { // if an alarm for this value does not exist yet (in the remove list)
					ChartAlarm ca = new ChartAlarm(value);
					parent.addChartAlarm(ca, true); // add alarm to StockerChart
					writeRemoveList(); // after adding, reload the currently present alarms
				}
			} catch (NumberFormatException nfe) {
				JOptionPane.showMessageDialog(SwingUtilities.windowForComponent(parent.getDesktopPane()),
						"Ungültige Eingabe beim Alarm-Wert,\nbitte korrigieren!", "Ungültige Eingabe",
						JOptionPane.WARNING_MESSAGE);
			}
		} else if (btn == btnRemove) {
			Iterator<ChartAlarm> removeIt = listRemove.getSelectedValuesList().iterator();
			while (removeIt.hasNext()) {
				parent.removeChartAlarm(removeIt.next(), true);
			}
			writeRemoveList();
		}
	}
}
