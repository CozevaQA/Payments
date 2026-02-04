package runner;

import javax.swing.*;
import java.awt.event.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.awt.Image;

import test.RunTest;

public class Main {

    public static String configPath;

    static Map<String, List<String>> customerPaymentOptions = new HashMap<>();
    static {
        customerPaymentOptions.put("Health Net", Arrays.asList("Registry", "Payment HTML"));
        customerPaymentOptions.put("Health Net (CAPCI)", Collections.singletonList("Registry"));
        customerPaymentOptions.put("BSC (CAPCI)", Collections.singletonList("Registry"));
        customerPaymentOptions.put("Molina", Collections.singletonList("Registry"));
        customerPaymentOptions.put("Oasis", Collections.singletonList("Registry"));
    }

    public static void main(String[] args) {

        configPath = args[0];

        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(configPath)) {
            properties.load(fis);
        } catch (IOException e) {
            System.out.println("Could not load config file: " + e.getMessage());
            return;
        }

        String logoPath = properties.getProperty("logo");

        JFrame frame = new JFrame("Select Customer and Payment Method");

        try {
            ImageIcon logoIcon = new ImageIcon(logoPath);
            Image logoImage = logoIcon.getImage();
            frame.setIconImage(logoImage);
        } catch (Exception e) {
            System.out.println("Could not load logo: " + e.getMessage());
        }

        frame.setSize(500, 350);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(null);

        JLabel customerLabel = new JLabel("Customer:");
        customerLabel.setBounds(30, 30, 120, 25);
        frame.add(customerLabel);

        JComboBox<String> customerBox =
                new JComboBox<>(customerPaymentOptions.keySet().toArray(new String[0]));
        customerBox.setBounds(160, 30, 280, 25);
        frame.add(customerBox);

        JLabel methodLabel = new JLabel("Payment Method:");
        methodLabel.setBounds(30, 70, 120, 25);
        frame.add(methodLabel);

        JComboBox<String> methodBox = new JComboBox<>();
        methodBox.setBounds(160, 70, 280, 25);
        frame.add(methodBox);

        JLabel envLabel = new JLabel("Environment:");
        envLabel.setBounds(30, 110, 120, 25);
        frame.add(envLabel);

        JComboBox<String> envBox = new JComboBox<>(new String[] { "Cert", "Prod" });
        envBox.setBounds(160, 110, 280, 25);
        frame.add(envBox);

        // ===== YEAR FIELD (ONLY FOR MOLINA) =====
        JLabel yearLabel = new JLabel("Year:");
        yearLabel.setBounds(30, 150, 120, 25);
        yearLabel.setVisible(false);
        frame.add(yearLabel);

        JComboBox<String> yearBox = new JComboBox<>(new String[] { "MY 2025", "MY 2024" });
        yearBox.setBounds(160, 150, 280, 25);
        yearBox.setVisible(false);
        frame.add(yearBox);
        // ======================================

        JButton runButton = new JButton("Run Test");
        runButton.setBounds(180, 210, 120, 30);
        frame.add(runButton);

        // 🔹 Customer selection logic
        customerBox.addActionListener(e -> {
            String selectedCustomer = (String) customerBox.getSelectedItem();
            List<String> methods =
                    customerPaymentOptions.getOrDefault(selectedCustomer, new ArrayList<>());

            methodBox.removeAllItems();
            for (String method : methods) {
                methodBox.addItem(method);
            }

            boolean isMolina = "Molina".equals(selectedCustomer);
            yearLabel.setVisible(isMolina);
            yearBox.setVisible(isMolina);

            if (isMolina) {
                // Auto-select MY 2025 every time Molina is selected
                yearBox.setSelectedIndex(0);
            } else {
                // Clear year for all non-Molina customers
                yearBox.setSelectedItem(null);
            }
        });

        customerBox.setSelectedIndex(0);

        // 🔹 Run button logic
        runButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                String customer = (String) customerBox.getSelectedItem();
                String method = (String) methodBox.getSelectedItem();
                String env = (String) envBox.getSelectedItem();

                // Year ONLY for Molina
                String year = null;
                if ("Molina".equals(customer)) {
                    year = (String) yearBox.getSelectedItem();
                }

                if (customer == null || method == null || env == null) {
                    JOptionPane.showMessageDialog(frame, "Please select all options.");
                    return;
                }

                frame.dispose();

                try {
                    RunTest run = new RunTest(env, customer, method, year);

                    System.out.println("Test execution started for: "
                            + customer + " - " + method + " - " + env);

                    if ("Health Net (CAPCI)".equals(customer)) {
                        if ("Registry".equalsIgnoreCase(method)) {
                            run.runHnetCAPCI();
                        }
                    }else if("BSC (CAPCI)".equals(customer)) {
                    	if ("Registry".equalsIgnoreCase(method)) {
                            run.runBSCCAPCI();
                        }
                    }
                    else if ("Health Net".equals(customer)) {
                        if ("Payment HTML".equalsIgnoreCase(method)) {
                            run.runPaymentHTML();
                        } else if ("Registry".equalsIgnoreCase(method)) {
                            run.runHnet();
                        }
                    } else if ("Molina".equals(customer)) {
                        if ("Registry".equalsIgnoreCase(method)) {
                            if ("MY 2025".equals(year)) {
                                run.runMolina_2025();
                            } else if ("MY 2024".equals(year)) {
                                run.runMolina_2024();
                            }
                        }
                    }

                    System.out.println("Test executed for: "
                            + customer + " - " + method + " - " + env);

                } catch (IOException ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage());
                }
            }
        });

        frame.setVisible(true);
    }
}
