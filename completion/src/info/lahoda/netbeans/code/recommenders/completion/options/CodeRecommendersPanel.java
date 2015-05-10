package info.lahoda.netbeans.code.recommenders.completion.options;

import info.lahoda.netbeans.code.recommenders.completion.Utils;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.*;
import org.openide.NotifyDescriptor.InputLine;

final class CodeRecommendersPanel extends javax.swing.JPanel {

    private final CodeRecommendersOptionsPanelController controller;

    CodeRecommendersPanel(CodeRecommendersOptionsPanelController controller) {
        this.controller = controller;
        initComponents();
        list.addListSelectionListener(new ListSelectionListener() {
            @Override public void valueChanged(ListSelectionEvent e) {
                updateButtonState();
            }
        });
        list.setSelectedIndex(0);
        updateButtonState();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        add = new javax.swing.JButton();
        remove = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        list = new javax.swing.JList();
        edit = new javax.swing.JButton();

        org.openide.awt.Mnemonics.setLocalizedText(add, org.openide.util.NbBundle.getMessage(CodeRecommendersPanel.class, "CodeRecommendersPanel.add.text", new Object[] {})); // NOI18N
        add.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(remove, org.openide.util.NbBundle.getMessage(CodeRecommendersPanel.class, "CodeRecommendersPanel.remove.text", new Object[] {})); // NOI18N
        remove.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeActionPerformed(evt);
            }
        });

        list.setModel(new DefaultListModel<String>());
        jScrollPane1.setViewportView(list);

        org.openide.awt.Mnemonics.setLocalizedText(edit, org.openide.util.NbBundle.getMessage(CodeRecommendersPanel.class, "CodeRecommendersPanel.edit.text", new Object[] {})); // NOI18N
        edit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 171, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(edit, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(remove, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(add, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(add)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(edit)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(remove)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 202, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void removeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeActionPerformed
        int selected = list.getSelectedIndex();
        DefaultListModel<String> model = (DefaultListModel<String>) list.getModel();
        model.removeElementAt(list.getSelectedIndex());
        if (selected >= model.size())
            selected = model.size() - 1;
        list.setSelectedIndex(selected);
        controller.changed();
    }//GEN-LAST:event_removeActionPerformed

    private void editActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editActionPerformed
        String inp = input(((DefaultListModel<String>) list.getModel()).getElementAt(list.getSelectedIndex()));

        if (inp != null) {
            ((DefaultListModel<String>) list.getModel()).setElementAt(inp, list.getSelectedIndex());
            controller.changed();
        }
    }//GEN-LAST:event_editActionPerformed

    private void addActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addActionPerformed
        String inp = input("");

        if (inp != null) {
            ((DefaultListModel<String>) list.getModel()).addElement(inp);
            controller.changed();
        }
    }//GEN-LAST:event_addActionPerformed

    private void updateButtonState() {
        edit.setEnabled(list.getSelectedIndex() != (-1));
        remove.setEnabled(list.getSelectedIndex() != (-1));
    }

    void load() {
        ((DefaultListModel<String>) list.getModel()).clear();
        for (String loc : Utils.getLocations()) {
            ((DefaultListModel<String>) list.getModel()).addElement(loc);
        }
    }

    void store() {
        List<String> locations = new ArrayList<>();
        DefaultListModel<String> model = (DefaultListModel<String>) list.getModel();

        for (int i = 0; i < model.size(); i++) {
            locations.add(model.getElementAt(i));
        }

        Utils.setLocations(locations);
    }

    boolean valid() {
        // TODO check whether form is consistent and complete
        return true;
    }

    private String input(String predefined) {
        InputLine inp = new DialogDescriptor.InputLine("Repository Location:", "Repository Location");
        inp.setInputText(predefined);

        if (DialogDisplayer.getDefault().notify(inp) == DialogDescriptor.OK_OPTION)
            return inp.getInputText();
        else
            return null;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton add;
    private javax.swing.JButton edit;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JList list;
    private javax.swing.JButton remove;
    // End of variables declaration//GEN-END:variables
}
