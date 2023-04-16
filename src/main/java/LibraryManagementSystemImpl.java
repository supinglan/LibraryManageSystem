import entities.Book;
import entities.Borrow;
import entities.Card;
import queries.*;
import utils.DBInitializer;
import utils.DatabaseConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LibraryManagementSystemImpl implements LibraryManagementSystem {

    private final DatabaseConnector connector;

    public LibraryManagementSystemImpl(DatabaseConnector connector) {
        this.connector = connector;
    }

    @Override
    public ApiResult storeBook(Book book) {
        Connection conn = connector.getConn();
        Object[] params = {book.getCategory(), book.getTitle(), book.getAuthor(), book.getPress(), book.getPublishYear(), book.getPrice(), book.getStock()};
        ResultSet rs;
        try {
            String sqlInsert = "insert into book(category, title, author, press,  publish_year, price, stock) values (?,?,?,?,?,?,?)";
            PreparedStatement insertStmt = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
            for (int i = 1; i <= 7; i++) insertStmt.setObject(i, params[i - 1]);
            insertStmt.executeUpdate();
            rs = insertStmt.getGeneratedKeys();
            if (rs.next()) {
                book.setBookId(rs.getInt(1));
            }
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "The book is stored successfully!");
    }

    @Override
    public ApiResult incBookStock(int bookId, int deltaStock) {
        Connection conn = connector.getConn();
        ResultSet rs;
        int newStock;
        try {
            String sqlSelect = "select stock from book where book_id = ?";
            String sqlUpdate = "update book set stock = ? where  book_id = ?";
            PreparedStatement selectStmt = conn.prepareStatement(sqlSelect);
            PreparedStatement updateStmt = conn.prepareStatement(sqlUpdate);
            selectStmt.setObject(1, bookId);
            rs = selectStmt.executeQuery();
            if (rs.next()) {
                newStock = rs.getInt(1) + deltaStock;
                if (newStock >= 0) {
                    updateStmt.setObject(1, newStock);
                    updateStmt.setObject(2, bookId);
                    if (updateStmt.executeUpdate() != 1)
                        return new ApiResult(false, "Fail: stock of the book has already been 0.");
                } else return new ApiResult(false, "Fail: stock of the book can't be less than 0.");
            } else {
                return new ApiResult(false, "Fail: the book is not in the library system.");
            }
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "Increment success!");
    }

    @Override
    public ApiResult storeBook(List<Book> books) {
        Connection conn = connector.getConn();
        Object[] params;
        ResultSet rs;
        Book book;
        try {
            for (Book value : books) {
                book = value;
                params = new Object[]{book.getCategory(), book.getTitle(), book.getAuthor(), book.getPress(), book.getPublishYear(), book.getPrice(), book.getStock()};
                String sqlSelect = "select book_id from book where (category, title, author,press, publish_year) = (?,?,?,?,?)";
                String sqlInsert = "insert into book(category, title, author, press,  publish_year, price, stock) values (?,?,?,?,?,?,?)";

                PreparedStatement selectStmt = conn.prepareStatement(sqlSelect);
                PreparedStatement insertStmt = conn.prepareStatement(sqlInsert);
                for (int i = 1; i <= 5; i++) selectStmt.setObject(i, params[i - 1]);
                rs = selectStmt.executeQuery();

                if (!rs.next()) {
                    //System.out.println(book);
                    for (int i = 1; i <= 7; i++) insertStmt.setObject(i, params[i - 1]);
                    insertStmt.executeUpdate();
                } else {
                    rollback(conn);
                    return new ApiResult(false, "Fail: There is book already in the library system!");
                }
                rs = selectStmt.executeQuery();
                if (rs.next()) book.setBookId(rs.getInt(1));
            }
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "The books are stored successfully!");
    }

    @Override
    public ApiResult removeBook(int bookId) {
        Connection conn = connector.getConn();
        ResultSet rs;
        try {
            String sqlSelect = "select * from borrow where book_id = ? and return_time =0";
            String sqlUpdate = "delete from book where  book_id = ?";

            PreparedStatement selectStmt = conn.prepareStatement(sqlSelect);
            PreparedStatement updateStmt = conn.prepareStatement(sqlUpdate);
            selectStmt.setObject(1, bookId);
            rs = selectStmt.executeQuery();
            if (!rs.next()) {
                updateStmt.setObject(1, bookId);
                if (updateStmt.executeUpdate() == 0) {
                    rollback(conn);

                    return new ApiResult(false, "Fail: the book is not in the library system.");
                }
            } else {
                rollback(conn);
                return new ApiResult(false, "Fail: Someone has not returned the book");
            }
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "Remove successfully!");
    }

    @Override
    public ApiResult modifyBookInfo(Book book) {
        Connection conn = connector.getConn();
        Object[] params = {book.getCategory(), book.getTitle(), book.getAuthor(), book.getPress(), book.getPublishYear(), book.getPrice()};
        int bookId = book.getBookId();
        try {
            String sqlUpdate = "update book set  category = ? , title = ?, author = ?, press = ?,  publish_year = ?, price = ? where  book_id = ?";
            PreparedStatement updateStmt = conn.prepareStatement(sqlUpdate);
            for (int i = 1; i <= 6; i++) updateStmt.setObject(i, params[i - 1]);
            updateStmt.setObject(7, bookId);
            if (updateStmt.executeUpdate() == 0) {
                rollback(conn);
                return new ApiResult(false, "Fail: the book is not in the library system.");
            }
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "Modify successfully!");
    }

    @Override
    public ApiResult queryBook(BookQueryConditions conditions) {
        Connection conn = connector.getConn();
        ResultSet rs;
        List<Book> res = new ArrayList<>();
        Book book;
        BookQueryResults result;
        int i;
        int maxy = Integer.MAX_VALUE, miny = Integer.MIN_VALUE;
        double maxp = Double.MAX_VALUE, minp = Double.MIN_VALUE;
        StringBuilder sb;
        try {
            String sqlSelect = "select * from book " + "where  publish_year >= ? and publish_year <= ? and price >= ? and price <= ? ";
            if (conditions.getCategory() != null) sqlSelect += " and category = ? ";
            if (conditions.getTitle() != null) sqlSelect += " and title like ? ";
            if (conditions.getPress() != null) sqlSelect += " and press like ? ";
            if (conditions.getAuthor() != null) sqlSelect += " and author like ? ";
            sqlSelect += "order by  ";
            sqlSelect += conditions.getSortBy().getValue();
            if (conditions.getSortOrder().equals(SortOrder.DESC)) sqlSelect += " DESC ";
            if (!conditions.getSortBy().getValue().equals("book_id")) sqlSelect += ",book_id";
            PreparedStatement selectStmt = conn.prepareStatement(sqlSelect);
            if (conditions.getMinPublishYear() != null) miny = conditions.getMinPublishYear();
            if (conditions.getMaxPublishYear() != null) maxy = conditions.getMaxPublishYear();
            if (conditions.getMinPrice() != null) minp = conditions.getMinPrice();
            if (conditions.getMaxPrice() != null) maxp = conditions.getMaxPrice();
            selectStmt.setInt(1, miny);
            selectStmt.setInt(2, maxy);
            selectStmt.setDouble(3, minp);
            selectStmt.setDouble(4, maxp);
            i = 5;
            if (conditions.getCategory() != null) {
                selectStmt.setString(i, conditions.getCategory());
                i++;
            }
            if (conditions.getTitle() != null) {
                sb = new StringBuilder(conditions.getTitle());
                sb.insert(0, "%");
                sb.append("%");
                selectStmt.setString(i, sb.toString());
                i++;
            }
            if (conditions.getPress() != null) {
                sb = new StringBuilder(conditions.getPress());
                sb.insert(0, "%");
                sb.append("%");
                selectStmt.setString(i, sb.toString());
                i++;
            }
            if (conditions.getAuthor() != null) {
                sb = new StringBuilder(conditions.getAuthor());
                sb.insert(0, "%");
                sb.append("%");
                selectStmt.setString(i, sb.toString());
                i++;
            }


            rs = selectStmt.executeQuery();
            while (rs.next()) {
                book = new Book(rs.getString("category"), rs.getString("title"), rs.getString("press"), rs.getInt("publish_year"), rs.getString("author"), rs.getDouble("price"), rs.getInt("stock"));
                book.setBookId(rs.getInt("book_id"));
                res.add(book);
            }
            result = new BookQueryResults(res);
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "Query successfully!", result);
    }

    @Override
    public ApiResult borrowBook(Borrow borrow) {
        Connection conn = connector.getConn();
        ResultSet rs;
        Object[] params = {borrow.getCardId(), borrow.getBookId(), borrow.getBorrowTime()};
        try {
            String sqlSelect1 = "select * from borrow where  return_time = 0 and card_id = ? and book_id = ? ";
            String sqlSelect2 = "select stock from book where book_id = ? for update";
            String sqlUpdate1 = "insert into borrow (card_id, book_id, borrow_time) values(?, ?, ?)";

            PreparedStatement selectStmt1 = conn.prepareStatement(sqlSelect1);
            PreparedStatement selectStmt2 = conn.prepareStatement(sqlSelect2);
            PreparedStatement updateStmt1 = conn.prepareStatement(sqlUpdate1);

            selectStmt1.setObject(1, params[0]);
            selectStmt1.setObject(2, params[1]);
            rs = selectStmt1.executeQuery();
            if (rs.next()) {
                rollback(conn);
                return new ApiResult(false, "Fail: The book has been borrowed and has not been returned.");
            }
            selectStmt2.setObject(1, params[1]);
            rs = selectStmt2.executeQuery();
            if (rs.next()) {
                if (rs.getInt(1) > 0) {
                    for (int i = 1; i <= 3; i++) {
                        updateStmt1.setObject(i, params[i - 1]);
                    }
                    if (updateStmt1.executeUpdate() != 1) {
                        System.out.println("Fail to borrow!");
                        return new ApiResult(false, "Fail to borrow!");
                    }
                    if (!this.incBookStock(borrow.getBookId(), -1).ok) {
                        System.out.println("Fail: The book stock is empty.");
                        return new ApiResult(false, "Fail: The book stock is empty.");
                    }
                } else {
                    rollback(conn);
                    return new ApiResult(false, "Fail: The book stock is empty.");
                }
            } else {
                rollback(conn);
                return new ApiResult(false, "Fail: The book is not in the library system.");
            }
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "Borrow successfully!");
    }


    @Override
    public ApiResult returnBook(Borrow borrow) {
        Connection conn = connector.getConn();
        Object[] params = {borrow.getReturnTime(), borrow.getCardId(), borrow.getBookId(), borrow.getBorrowTime()};
        try {
            String sqlUpdate = "update borrow set return_time = ? where (card_id, book_id,borrow_time) = (?, ?, ?) and return_time = 0 ";
            PreparedStatement updateStmt = conn.prepareStatement(sqlUpdate);
            for (int i = 1; i <= 4; i++) {
                updateStmt.setObject(i, params[i - 1]);
            }
            if (borrow.getBorrowTime() > borrow.getReturnTime()) {
                rollback(conn);
                return new ApiResult(false, "Fail: Time error");
            }
            if (updateStmt.executeUpdate() == 1) {
                this.incBookStock(borrow.getBookId(), 1);

            } else {
                rollback(conn);
                return new ApiResult(false, "Fail: There is no record of this loan.");
            }
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "Return successfully!");
    }

    @Override
    public ApiResult showBorrowHistory(int cardId) {
        Connection conn = connector.getConn();
        ResultSet rs;
        Book book;
        Borrow borrow;
        BorrowHistories result;
        List<BorrowHistories.Item> array = new ArrayList<>();
        try {
            String sqlSelect = "select * from borrow natural join book where card_id = ? order by borrow_time DESC, book_id";
            PreparedStatement selectStmt = conn.prepareStatement(sqlSelect);
            selectStmt.setObject(1, cardId);
            rs = selectStmt.executeQuery();
            while (rs.next()) {
                book = new Book(rs.getString("category"), rs.getString("title"), rs.getString("press"), rs.getInt("publish_year"), rs.getString("author"), rs.getDouble("price"), rs.getInt("stock"));
                book.setBookId(rs.getInt("book_id"));
                borrow = new Borrow(rs.getInt("book_id"), rs.getInt("card_id"));
                borrow.setBorrowTime(rs.getLong("borrow_time"));
                borrow.setReturnTime(rs.getLong("return_time"));
                array.add(new BorrowHistories.Item(cardId, book, borrow));
            }

            result = new BorrowHistories(array);
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "Show borrow history successfully!", result);
    }

    @Override
    public ApiResult registerCard(Card card) {
        Connection conn = connector.getConn();
        ResultSet rs;
        try {
            String sqlInsert = "insert into card(name, department,type) values (?,?,?)";
            PreparedStatement insertStmt = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, card.getName());
            insertStmt.setString(2, card.getDepartment());
            insertStmt.setObject(3, card.getType().getStr());
            insertStmt.executeUpdate();
            rs = insertStmt.getGeneratedKeys();
            if (rs.next()) {
                card.setCardId(rs.getInt(1));
            }
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "The book is stored successfully!");
    }

    @Override
    public ApiResult removeCard(int cardId) {
        Connection conn = connector.getConn();
        ResultSet rs;
        try {
            String sqlSelect = "select * from borrow where card_id = ? and return_time = 0";
            String sqlUpdate = "delete from card where  card_id = ?";

            PreparedStatement selectStmt = conn.prepareStatement(sqlSelect);
            PreparedStatement updateStmt = conn.prepareStatement(sqlUpdate);
            selectStmt.setObject(1, cardId);
            rs = selectStmt.executeQuery();
            if (!rs.next()) {
                updateStmt.setObject(1, cardId);
                if (updateStmt.executeUpdate() == 0) {
                    rollback(conn);
                    return new ApiResult(false, "Fail: The card is not in the library system.");
                }
            } else {
                rollback(conn);
                return new ApiResult(false, "Fail: The card has unreturned books");
            }
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "Remove successfully!");
    }


    @Override
    public ApiResult showCards() {
        Connection conn = connector.getConn();
        ResultSet rs;
        CardList result;
        List<Card> array = new ArrayList<>();
        try {
            String sqlSelect = "select * from card order by card_id";
            PreparedStatement selectStmt = conn.prepareStatement(sqlSelect);
            rs = selectStmt.executeQuery();
            while (rs.next()) {
                array.add(new Card(rs.getInt(1), rs.getString(2), rs.getString(3), Card.CardType.values(rs.getString(4))));
            }
            result = new CardList(array);
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, "Show cards successfully!", result);
    }

    @Override
    public ApiResult resetDatabase() {
        Connection conn = connector.getConn();
        try {
            Statement stmt = conn.createStatement();
            DBInitializer initializer = connector.getConf().getType().getDbInitializer();
            stmt.addBatch(initializer.sqlDropBorrow());
            stmt.addBatch(initializer.sqlDropBook());
            stmt.addBatch(initializer.sqlDropCard());
            stmt.addBatch(initializer.sqlCreateCard());
            stmt.addBatch(initializer.sqlCreateBook());
            stmt.addBatch(initializer.sqlCreateBorrow());
            stmt.executeBatch();
            commit(conn);
        } catch (Exception e) {
            rollback(conn);
            return new ApiResult(false, e.getMessage());
        }
        return new ApiResult(true, null);
    }

    private void rollback(Connection conn) {
        try {
            conn.rollback();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void commit(Connection conn) {
        try {
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
