package berkeleyadvanced;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeClientFrame extends JFrame {
    private String serverIP;
    private JLabel lblClock;
    private JTable tblLog;
    private DefaultTableModel tableModel;
    private JTextField txtSetTime;
    
    private long timeOffset = 0; // Độ lệch giả lập
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    public TimeClientFrame(String ip) {
        this.serverIP = ip;
        initComponents();
        startClock();
        connectToServer();
    }

    private void initComponents() {
        setTitle("CLIENT (SLAVE) - " + serverIP);
        setSize(500, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Top: Đồng hồ
        JPanel pnlTop = new JPanel(new BorderLayout());
        pnlTop.setBackground(Color.BLACK);
        
        lblClock = new JLabel("Loading...");
        lblClock.setFont(new Font("Consolas", Font.BOLD, 45));
        lblClock.setForeground(Color.CYAN);
        lblClock.setHorizontalAlignment(SwingConstants.CENTER);
        pnlTop.add(lblClock, BorderLayout.CENTER);
        add(pnlTop, BorderLayout.NORTH);

        // 2. Center: Bảng Log (100 dòng)
        String[] cols = {"Thời gian", "Nội dung"};
        tableModel = new DefaultTableModel(cols, 0);
        tblLog = new JTable(tableModel);
        
        // Renderer tô màu chữ: Gửi đỏ, Nhận xanh
        tblLog.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String content = (String) table.getModel().getValueAt(row, 1);
                if (content.startsWith("SEND")) c.setForeground(Color.RED);
                else if (content.startsWith("RECV")) c.setForeground(Color.BLUE);
                else c.setForeground(Color.BLACK);
                return c;
            }
        });
        
        add(new JScrollPane(tblLog), BorderLayout.CENTER);

        // 3. Bottom: Chỉnh giờ giả lập
        JPanel pnlBot = new JPanel(new FlowLayout());
        pnlBot.add(new JLabel("Giả lập giờ (HH:mm:ss): "));
        txtSetTime = new JTextField(10);
        JButton btnSet = new JButton("Thay đổi");
        
        btnSet.addActionListener(e -> {
            try {
                String input = txtSetTime.getText();
                // Parse giờ nhập vào ngày hiện tại
                SimpleDateFormat inputFmt = new SimpleDateFormat("HH:mm:ss");
                Date dateInput = inputFmt.parse(input);
                
                // Lấy ngày hiện tại ghép với giờ nhập
                Date now = new Date();
                dateInput.setYear(now.getYear());
                dateInput.setMonth(now.getMonth());
                dateInput.setDate(now.getDate());
                
                // Tính offset mới
                timeOffset = dateInput.getTime() - System.currentTimeMillis();
                JOptionPane.showMessageDialog(this, "Đã chỉnh giờ giả lập!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Sai định dạng HH:mm:ss");
            }
        });
        
        pnlBot.add(txtSetTime);
        pnlBot.add(btnSet);
        add(pnlBot, BorderLayout.SOUTH);
    }

    private void startClock() {
        new javax.swing.Timer(50, e -> {
            long simTime = System.currentTimeMillis() + timeOffset;
            lblClock.setText(timeFormat.format(new Date(simTime)));
        }).start();
    }

    private void addLog(String type, String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = timeFormat.format(new Date(System.currentTimeMillis() + timeOffset));
            tableModel.insertRow(0, new Object[]{time, type + ": " + msg});
            if (tableModel.getRowCount() > 100) tableModel.setRowCount(100);
        });
    }

    private void connectToServer() {
        new Thread(() -> {
            while (true) {
                try (Socket socket = new Socket(serverIP, 9999);
                     DataInputStream in = new DataInputStream(socket.getInputStream());
                     DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                    
                    addLog("INFO", "Đã kết nối Server");

                    while (true) {
                        String cmd = in.readUTF();
                        if (cmd.equals("REQ")) {
                            // Server yêu cầu time -> Gửi đi (Dòng đỏ)
                            long myTime = System.currentTimeMillis() + timeOffset;
                            out.writeLong(myTime);
                            addLog("SEND", "Gửi time " + myTime);
                        } 
                        else if (cmd.equals("ADJ")) {
                            // Server yêu cầu chỉnh (Dòng xanh)
                            long adj = in.readLong();
                            timeOffset += adj;
                            addLog("RECV", "Nhận offset " + adj + "ms. Đã đồng bộ!");
                        }
                    }
                } catch (Exception e) {
                    addLog("ERROR", "Mất kết nối. Thử lại sau 3s...");
                    try { Thread.sleep(3000); } catch (Exception ex) {}
                }
            }
        }).start();
    }
}