
package berkeleyadvanced; 


import javax.swing.*;

public class BerkeleyAdvanced {

    public static void main(String[] args) {
        // Thiết lập giao diện đẹp (Windows look and feel)
        try { 
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); 
        } catch (Exception e) {}

        // Tạo hộp thoại chọn vai trò
        Object[] options = {"Chạy Server (Master)", "Chạy Client (Slave)"};
        int choice = JOptionPane.showOptionDialog(null,
                "Bạn muốn chạy vai trò gì trên máy này?",
                "Cấu hình Berkeley System",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == 0) {
            // Nếu chọn Server -> Mở giao diện Server
            // Lưu ý: Bạn phải tạo file TimeServerFrame.java rồi thì dòng này mới không báo lỗi
            new TimeServerFrame().setVisible(true);
        } else if (choice == 1) {
            // Nếu chọn Client -> Hỏi IP Server -> Mở giao diện Client
            String ip = JOptionPane.showInputDialog("Nhập địa chỉ IP máy Server:", "localhost");
            
            // Nếu người dùng nhấn Cancel hoặc không nhập gì thì thoát
            if (ip != null && !ip.trim().isEmpty()) {
                // Lưu ý: Bạn phải tạo file TimeClientFrame.java rồi thì dòng này mới không báo lỗi
                new TimeClientFrame(ip).setVisible(true);
            }
        }
    }
}