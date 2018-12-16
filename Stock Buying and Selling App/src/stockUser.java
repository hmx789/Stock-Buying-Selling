// Complete this file by 11/26/18

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.TreeSet;
import java.util.Scanner;

public class stockUser
{
    public static Scanner scan = new Scanner(System.in);
    public static Connection con = getConnection("database_host","database_password");

    public static void main(String[] args) throws SQLException {
        boolean flag = true;
        int type;
        while(flag) {
            int name = getUserID(); // To quit the program enter quit
            if (name == 0) {

                flag = false;
                continue;
            }
            type = getTransactionType();

            switch(type) {
                case 1:
                    buyStocks(name);
                    break;
                case 2:
                    sellStocks(name);
                    break;
                default:
                    flag = false;
            }

        }
    }

    private static void buyStocks(int userID) throws SQLException {
        int lotNo; // no negative ids
        char input;
        TreeSet<Integer> lots = new TreeSet<>();
        String sql = "SELECT * from SellOrder natural join CompanyInfo;";
        do {
            System.out.println("Here are the currently available lots to buy from:");
            viewTable(sql,lots,false);        // Displaying available lots to buy from
            System.out.print("Which lot would you like to purchase (0 to exit)? "); //Asking input for what they would like to purchase
            lotNo = scan.nextInt();
            if (lotNo != 0)
                lots.add(lotNo);
        }while (lotNo != 0);

        if (lots.isEmpty())
            return;

        System.out.println("You are purchasing");
        double totalPrice = viewTable(sql,lots,true);
        System.out.print("Your total comes to " + String.format("%.2f",totalPrice) + ". Confirm purchase (y/n): ");
        input = scan.next().charAt(0);
        switch(input) {
            case 'y':
                ResultSet rs = getResults("select Balance from Person where AccountID = " + userID + ";" );
                if (rs.next()) {
                    if (rs.getDouble("Balance") < totalPrice) {
                        System.out.println("Insufficient funds to complete transaction, returning to main menu.");
                        return;
                    }
                }
                executeBuy(lots,userID);
                break;
            case 'n':
                break;
        }
    }


    private static void executeBuy(TreeSet<Integer> lots,int accID)  {
        Statement stmt = null;
        ResultSet rs = null;

        if (lots.isEmpty()) {
            System.out.println("No lots chosen, going back to main menu.");
            return;
        }
        try {
            stmt = con.createStatement();
            stmt.execute("use cs480fa2018");
            con.setAutoCommit(false);

        }catch (SQLException ex) {
            System.out.println("Sql exception in executeBuy!");
            return;
        }
        int transID = 0;
        int oldAccID = 0;
        int companyID = 0;
        int quantity = 0;
        double price = 0.00;

        for (int x : lots) {
            try {
                rs = stmt.executeQuery("Select * from SellOrder where TransactionID = " + x + ";");
                if (rs.next()) {
                    transID = rs.getInt("TransactionID");
                    oldAccID = rs.getInt("AccountID");
                    companyID = rs.getInt("CompanyID");
                    quantity = rs.getInt("Quantity");
                    price = rs.getDouble("Price");
                }

                //Updating purchaser balance
                stmt.executeUpdate("update Person set Balance = Balance - " + (price*quantity) +
                                    " where AccountID = '" + accID + "';");

                //Updating seller balance
                stmt.executeUpdate("update Person set Balance = Balance + " + (price*quantity) +
                                    " where AccountID = " + oldAccID + ";");

                //Updating the sellers stock amount
                stmt.executeUpdate("update Stock set Quantity = Quantity - " + quantity +
                                    " where AccountID = " + oldAccID + " and CompanyID = " + companyID + " " +
                                    "and Quantity >= "+ quantity + " LIMIT 1;");

                // Delete the SellOrder from the SellOrder table
                stmt.executeUpdate("delete from SellOrder where TransactionID = " + transID + ";" );

                // Insert the stock into stock table for the user who just bought this stock
                stmt.executeUpdate(String.format("insert into Stock(CompanyID,AccountID,Quantity) " +
                                                "values(%d,%d,%d)",companyID,accID,quantity));


            }catch (SQLException ex) {
                System.out.println("Sql exception in executeBuy inside the foreach!");
                try{con.rollback();}catch (SQLException ex1) {System.out.println("Failed inside buyStocks");}
            }


        }

        try {
            con.commit();
            rs = getResults("Select * from Person where AccountID = " + accID + ";");
            rs.next();
            double balance = rs.getDouble("Balance");
            System.out.println("Transaction Completed. Your balance is now $" + String.format("%.2f",balance));
        } catch (SQLException e) {
            try {con.rollback();}catch (SQLException ex1) { System.out.println("Insufficient funds...returning to main menu"); }
            e.printStackTrace();

        }finally {
            try{con.setAutoCommit(true);}catch(SQLException ex2){System.out.println("Could not turn on auto commit");}

        }


    }

    private static void sellStocks(int userID) {
        int lotNo;
        char input;
        double totalPrice;
        TreeSet<Integer> lots = new TreeSet<>();
        String sql = "SELECT * from BuyOrder natural join CompanyInfo;";
        do {
            System.out.println("Here are the currently available lots to sell to:");
            viewTable(sql,lots,false);    //avail lots
            System.out.print("Which lot would you like to sell to (0 to exit)? ");
            lotNo = scan.nextInt(); // what lots wanted
            if (lotNo != 0)
                lots.add(lotNo);
        }while (lotNo != 0);

        if (lots.isEmpty())
            return;

        System.out.println("You are selling");
        totalPrice = viewTable(sql,lots,true);
        System.out.println("This will leave you with");
        // Show what stocks she has left
        if(!stocksLeft(lots,userID)) {
            return;
        }
        System.out.println("Confirm purchase (y/n): ");
        input = scan.next().charAt(0);
        switch(input) {
            case 'y':
                executeSell(lots,userID);
                break;
            case 'n':
                break;

        }

    }
    private static void executeSell(TreeSet<Integer> lots, int userID) {
        Statement stmt = null;
        ResultSet rs = null;
        int oldAccID = 0;
        double total = 0.00;
        try {
            stmt = con.createStatement();
            stmt.execute("use cs480fa2018");
            con.setAutoCommit(false);
        }catch (SQLException ex) {System.out.println("Exception in creating or executing cs480fa2018");}

        for (int x : lots) {
            try {
                rs = stmt.executeQuery("Select * from BuyOrder where TransactionID = " + x + ";");
                if (!rs.next())
                    return;
                int transID = rs.getInt("TransactionID");
                oldAccID = rs.getInt("AccountID");
                int companyID = rs.getInt("CompanyID");
                int quantity = rs.getInt("Quantity");
                double price = rs.getDouble("Price");
                double totalPrice = quantity * price;
                total += totalPrice;
                // Decreasing balance from user who put in the BuyOrder
                stmt.executeUpdate("update Person set Balance = Balance - " + totalPrice +
                        " where AccountID = " + oldAccID + ";");

                //Increasing balance to the person who just sold
                stmt.executeUpdate("update Person set Balance = Balance + " + totalPrice +
                         " where AccountID = " + userID + ";");

                //Decreasing stock quantity for user who just sold
                stmt.executeUpdate("update Stock " +
                                    "set Quantity = Quantity - " + quantity +
                                    " where AccountID = " + userID + " and CompanyID = " + companyID
                                    + " and Quantity >= " + quantity + " LIMIT 1;" );


                //Delete the BuyOrder from the BuyOrder table
                stmt.executeUpdate(String.format("delete from BuyOrder where TransactionID = %d;",x));

                //Inserting the stock for the account holder of the BuyOrder
                stmt.executeUpdate(String.format("insert into Stock(CompanyID,AccountID,Quantity) " +
                                    "values(%d,%d,%d)",companyID,oldAccID,quantity));




            }catch (SQLException ex) { System.out.println("SQLException: " + ex.getMessage());
                System.out.println("SQLState: " + ex.getSQLState());
                System.out.println("VendorError: " + ex.getErrorCode());}
        }


        try {
            rs = getResults("Select * from Person where AccountID = " + oldAccID + ";");
            rs.next();
            if (rs.getDouble("Balance") < total) {
                System.out.println("One or more buyers has insufficient funds to complete transaction, returning to main menu.");
                return;
            }
            con.commit();
            rs = getResults("select * from Person where AccountID = " + userID +";");
            rs.next();
            double balance = rs.getDouble("Balance");
            System.out.println("Transaction completed. Your balance is now $" + String.format("%.2f",balance));
        } catch (SQLException e) {
            try {
                con.rollback();
                System.out.println("One or more buyers has insufficient funds to complete transaction, returning to main menu.");
            }catch (SQLException ex1) { System.out.println("Can't rollback"); }
            e.printStackTrace();

        }

    }


    private static int getUserID() {
        System.out.print("Which user are you? ");
        String name = scan.next();
        Statement stmt = null;
        ResultSet rs = null;
        int accID = 0;
        try {
           stmt = con.createStatement();
           stmt.execute("use cs480fa2018;");
           rs = stmt.executeQuery("Select * from Person where AccountName like '" + name + "';" );

            if (rs.next()) {
               accID = rs.getInt("AccountID");
           }

        }catch (SQLException ex) {
            System.out.println("User does not exist");
        }

        return accID;
    }

    private static int getTransactionType() {
        int type;
        do {
            System.out.print("Are you buying or selling(1 to buy, 2 to sell)? ");
            type = scan.nextInt();
        }while(type != 1 && type != 2);

        return type;
    }

    private static Connection getConnection(String url, String userPw) {
        Connection conn = null;

        try {
            // The newInstance() call is a work around for some
            // broken Java implementations
            Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
        } catch (Exception ex) {
            ex.printStackTrace();
            // handle the error
        }

        try {
            conn = DriverManager.getConnection(url + userPw);

        } catch (SQLException ex) {
            // handle any errors
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
            conn = null;
        }

        return conn;

    }
    private static double viewTable(String query,TreeSet<Integer> lots,boolean lotsChosen) {
        Statement stmt = null;
        ResultSet rs = null;
        double totalPrice = 0;
        try {
            stmt = con.createStatement();
            stmt.execute("USE cs480fa2018;");
            rs = stmt.executeQuery(query);
            while(rs.next()) {
                if (!lotsChosen) {
                    if (!lots.contains(rs.getInt("TransactionID"))) {
                        System.out.println(rs.getString("TransactionID") + "\t" + rs.getString("TickerName")
                                + "\t\t" + rs.getString("Quantity") + " at $" + rs.getString("Price") + " a share");
                    }
                }
                else {
                    if (lots.contains(rs.getInt("TransactionID"))) {
                        System.out.println(rs.getString("TransactionID") + "\t" + rs.getString("TickerName")
                                + "\t\t" + rs.getString("Quantity") + " at $" + rs.getString("Price") + " a share");
                        if (query.contains("SellOrder")) {
                            totalPrice += (rs.getInt("Quantity") * rs.getDouble("Price"));
                        }
                        else
                            totalPrice += rs.getInt("Quantity");
                    }
                }
            }
        }
        catch (SQLException ex) {
            System.out.println("SQLException: " + ex.getMessage());
            System.out.println("SQLState: " + ex.getSQLState());
            System.out.println("VendorError: " + ex.getErrorCode());
        }
        return totalPrice;
    }

    public static ResultSet getResults(String query) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            stmt = con.createStatement();
            rs = stmt.executeQuery(query);
        }catch (SQLException ex) {
            System.out.println("SQL Exception! inside getResults");
        }

        return rs;
    }

    public static boolean stocksLeft(TreeSet<Integer> lots, int AccountID ) {
        Statement stmt = null;
        Statement stmt2 = null;
        ResultSet rs = null;
        ResultSet rs2 = null;
        int userQuantity = 0;
        int sellingQuantity = 0;
        String tickerName;
        for (int x : lots) {
            try {
                stmt = con.createStatement();
                stmt2 = con.createStatement();
                rs = stmt.executeQuery("select * from BuyOrder natural join CompanyInfo where TransactionID = " + x + ";");

                if (!rs.next()) {
                    System.out.println("No shares");
                    return false;
                }

                rs2 = stmt2.executeQuery("select * from Stock where AccountID = " + AccountID + ";");

                if (!rs2.next()) {
                    System.out.println("No shares");
                    return false;
                }

                tickerName = rs.getString("TickerName");
                sellingQuantity = rs.getInt("Quantity");
                userQuantity = rs2.getInt("Quantity");
                int quantityLeft = userQuantity - sellingQuantity;
                if (quantityLeft < 0) {
                    System.out.println("Insufficient amount of stocks..returning to main menu");
                    return false;
                }
                System.out.println(tickerName + "\t\t" + quantityLeft + " shares");

            }catch (SQLException ex) {
                System.out.println("Inside stocksLeft SQL Exception");
            }
        }
        return true;
    }

}

