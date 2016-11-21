package org.jdbi.examples.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.joda.money.CurrencyUnit.USD;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.jdbi.examples.rule.DataSourceRule;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.StatementContext;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.ArgumentFactory;
import org.jdbi.v3.core.mapper.BeanMapper;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.sqlobject.BindBean;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.sqlobject.SqlQuery;
import org.jdbi.v3.sqlobject.SqlUpdate;
import org.jdbi.v3.sqlobject.customizers.RegisterArgumentFactory;
import org.jdbi.v3.sqlobject.customizers.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizers.RegisterColumnMapper;
import org.joda.money.Money;
import org.junit.Rule;
import org.junit.Test;

public class Example05SqlObjectApi {
  @Rule
  public DataSourceRule ds = new DataSourceRule();

  @RegisterBeanMapper(Something.class)
  @RegisterColumnMapper(MoneyMapper.class)
  @RegisterArgumentFactory(MoneyArgumentFactory.class)
  public interface SomethingDao {
    @SqlUpdate("create table something (id int primary key, name varchar(100), amount decimal)")
    void createTable();

    @SqlUpdate("insert into something (id, name, amount) values (:id, :name, :amount)")
    void insert(@BindBean Something something);

    @SqlUpdate("update something set name = :name, amount = :amount where id = :id")
    void update(@BindBean Something something);

    @SqlQuery("select * from something order by id")
    List<Something> list();

    @SqlQuery("select * from something where id = :id")
    Something getById(int id);
  }

  @Test
  public void test() throws Exception {
    Jdbi jdbi = Jdbi.create(ds.getDataSource());
    jdbi.installPlugin(new SqlObjectPlugin());
//    jdbi.registerRowMapper(BeanMapper.of(Something.class));
//    jdbi.registerColumnMapper(new MoneyMapper());
//    jdbi.registerArgumentFactory(new MoneyArgumentFactory());

    jdbi.useExtension(SomethingDao.class, dao -> {
      Money tenDollars = Money.of(USD, 10);
      Money fiveDollars = Money.of(USD, 5);

      dao.createTable();
      dao.insert(new Something(1, "Alice", tenDollars));
      dao.insert(new Something(2, "Bob", fiveDollars));

      assertThat(dao.list())
          .extracting(Something::getId, Something::getName, Something::getAmount)
          .containsExactly(tuple(1, "Alice", tenDollars),
                           tuple(2, "Bob", fiveDollars));

      assertThat(dao.getById(2))
          .extracting(Something::getId, Something::getName, Something::getAmount)
          .containsExactly(2, "Bob", fiveDollars);

      dao.update(new Something(2, "Robert", tenDollars));

      assertThat(dao.getById(2))
          .extracting(Something::getId, Something::getName, Something::getAmount)
          .containsExactly(2, "Robert", tenDollars);
    });
  }

  public static class Something {
    private int id;
    private String name;
    private Money amount;

    public Something() {
    }

    public Something(int id, String name, Money amount) {
      this.id = id;
      this.name = name;
      this.amount = amount;
    }

    public int getId() {
      return id;
    }

    public void setId(int id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Money getAmount() {
      return amount;
    }

    public void setAmount(Money amount) {
      this.amount = amount;
    }
  }

  public static class MoneyArgumentFactory implements ArgumentFactory {
    @Override
    public Optional<Argument> build(Type type, Object value, StatementContext ctx) {
      if (Money.class.equals(type)) {
        BigDecimal amount = value == null ? null : ((Money) value).getAmount();
        return Optional.of((pos, stmt, context) -> stmt.setBigDecimal(pos, amount));
      }
      return Optional.empty();
    }
  }

  public static class MoneyMapper implements ColumnMapper<Money> {
    @Override
    public Money map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
      return Money.of(USD, r.getBigDecimal(columnNumber));
    }
  }

}