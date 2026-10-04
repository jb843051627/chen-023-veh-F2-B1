package phtest;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.common.conf.V2Config;
import com.fc.v2.mapper.custom.TVehBindPageBoardMapper;
import com.fc.v2.model.auto.TVehBindPage;
import com.fc.v2.model.custom.VehBindBoard;
import com.fc.v2.model.custom.VehBindQuery;
import com.fc.v2.service.impl.TVehBindPageServiceImpl;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

@SpringBootTest(classes = RealBoardTest.Cfg.class)
class RealBoardTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.fc.v2.mapper.custom")
    static class Cfg {
        @Bean
        @Primary
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:real;MODE=MySQL;DB_CLOSE_DELAY=-1")
                    .driverClassName("org.h2.Driver")
                    .username("sa").password("").build();
        }

        @Bean
        public DataSourceInitializer init(DataSource ds) {
            StringBuilder sql = new StringBuilder();
            sql.append("CREATE TABLE t_veh_bind_page (")
               .append("id BIGINT PRIMARY KEY, bill_no VARCHAR(64), sign_no VARCHAR(64), veh_code VARCHAR(64),")
               .append("node_no INT, site_id INT, site_no VARCHAR(64), should_count INT, done_count INT,")
               .append("lack_count INT, content VARCHAR(255), status INT, del_flag INT,")
               .append("create_by VARCHAR(64), create_time TIMESTAMP, update_by VARCHAR(64), update_time TIMESTAMP,")
               .append("remark VARCHAR(500));");
            sql.append("CREATE TABLE t_veh_ent (")
               .append("id BIGINT PRIMARY KEY, site_no VARCHAR(64), site_name VARCHAR(128),")
               .append("site_type VARCHAR(32), road_name VARCHAR(128), status INT, del_flag INT);");
            sql.append("INSERT INTO t_veh_ent VALUES (0,'YH00','顺达','道路旅客运输业户','宁川省—宁州市—临江县',0,0);");
            // 13 张页：节点0，业户0；交错 status 0新录/1已联齐/2缺项
            for (int n = 1; n <= 13; n++) {
                int st = n % 3;
                sql.append("INSERT INTO t_veh_bind_page VALUES (")
                   .append(n).append(",'QD").append(n).append("','QR").append(n).append("'")
                   .append(",'VC").append(n).append("',0,0,'YH00',10,").append(st == 1 ? 10 : 5)
                   .append(",").append(st == 1 ? 0 : 5).append(",'c',").append(st).append(",0,'seed',NULL,NULL,NULL,NULL);");
            }
            DataSourceInitializer i = new DataSourceInitializer();
            i.setDataSource(ds);
            i.setDatabasePopulator(new ResourceDatabasePopulator(
                    new ByteArrayResource(sql.toString().getBytes(StandardCharsets.UTF_8))));
            return i;
        }

        @Bean
        public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean fb = new MybatisSqlSessionFactoryBean();
            fb.setDataSource(ds);
            fb.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mybatis/custom/TVehBindPageBoardMapper.xml"));
            fb.setTypeAliasesPackage("com.fc.v2.model.auto,com.fc.v2.model.custom");
            MybatisConfiguration c = new MybatisConfiguration();
            c.setMapUnderscoreToCamelCase(true);
            c.setCallSettersOnNulls(true);
            fb.setConfiguration(c);
            fb.setGlobalConfig(new GlobalConfig());
            return fb.getObject();
        }

        // 复刻项目里遗留的 PageHelperConf：一个 PageHelper bean（5.3.2 已不是 Interceptor）
        @Bean
        public com.github.pagehelper.PageHelper pageHelper() {
            com.github.pagehelper.PageHelper ph = new com.github.pagehelper.PageHelper();
            java.util.Properties p = new java.util.Properties();
            p.setProperty("offsetAsPageNum", "true");
            p.setProperty("rowBoundsWithCount", "true");
            p.setProperty("reasonable", "true");
            p.setProperty("dialect", "mysql");
            ph.setProperties(p);
            return ph;
        }
    }

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    private TVehBindPageBoardMapper boardMapper;

    private TVehBindPageServiceImpl service;

    @BeforeEach
    void wire() {
        boardMapper = sqlSessionFactory.openSession().getMapper(TVehBindPageBoardMapper.class);
        service = new TVehBindPageServiceImpl();
        V2Config cfg = new V2Config();
        cfg.setVehBindPageSize(3);
        ReflectionTestUtils.setField(service, "boardMapper", boardMapper);
        ReflectionTestUtils.setField(service, "v2Config", cfg);
    }

    private VehBindQuery baseQuery() {
        VehBindQuery q = new VehBindQuery();
        q.setNodeNo(0);
        q.setRoadPrefix("宁川省");
        return q;
    }

    @Test
    void rawMapper_pageHelper() {
        System.out.println(">>> interceptors: " + sqlSessionFactory.getConfiguration().getInterceptors());
        PageHelper.startPage(1, 3, false);
        List<TVehBindPage> rows = boardMapper.selectBoardPage(baseQuery());
        PageInfo<TVehBindPage> p = new PageInfo<TVehBindPage>(rows);
        System.out.println(">>> rawMapper rows=" + rows.size() + " total=" + p.getTotal()
                + " window=" + (rows.isEmpty() ? "-" : rows.get(0).getBoardTotal()));
    }

    @Test
    void service_openBoard_defaultPageSize3() {
        VehBindBoard b = service.openBoard(baseQuery());
        System.out.println(">>> default pages=" + b.getPages().size()
                + " total=" + b.getTotalCount() + " linked=" + b.getLinkedCount()
                + " lack=" + b.getLackCount());
        // 每页 3 条
        org.junit.jupiter.api.Assertions.assertEquals(3, b.getPages().size());
        // 统计与明细同源：挂页 = 已联齐 + 缺项（13 = 5 + 8）
        org.junit.jupiter.api.Assertions.assertEquals(13, b.getTotalCount());
        org.junit.jupiter.api.Assertions.assertEquals(5, b.getLinkedCount());
        org.junit.jupiter.api.Assertions.assertEquals(8, b.getLackCount());
        org.junit.jupiter.api.Assertions.assertEquals(b.getTotalCount(),
                b.getLinkedCount() + b.getLackCount(), "三栏必须接得平");
    }

    @Test
    void filter_linked() {
        VehBindQuery q = baseQuery();
        q.setStatus(1); // 已联齐：lack_count=0
        VehBindBoard b = service.openBoard(q);
        System.out.println(">>> filter linked total=" + b.getTotalCount() + " linked=" + b.getLinkedCount());
        org.junit.jupiter.api.Assertions.assertEquals(5, b.getTotalCount());
        for (TVehBindPage p : b.getPages()) {
            org.junit.jupiter.api.Assertions.assertEquals(0, p.getLackCount().intValue());
        }
    }

    @Test
    void filter_lack() {
        VehBindQuery q = baseQuery();
        q.setStatus(2); // 缺项：lack_count>0
        VehBindBoard b = service.openBoard(q);
        System.out.println(">>> filter lack total=" + b.getTotalCount() + " lack=" + b.getLackCount());
        org.junit.jupiter.api.Assertions.assertEquals(8, b.getTotalCount());
        for (TVehBindPage p : b.getPages()) {
            org.junit.jupiter.api.Assertions.assertTrue(p.getLackCount() > 0);
        }
    }

    @Test
    void service_openBoard_page2_limit3() {
        VehBindQuery q = baseQuery();
        q.setPage(2);
        q.setLimit(3);
        VehBindBoard b = service.openBoard(q);
        System.out.println(">>> page2 pages=" + b.getPages().size() + " total=" + b.getTotalCount());
        for (TVehBindPage p : b.getPages()) {
            System.out.println("    id=" + p.getId() + " status=" + p.getStatus()
                    + " should=" + p.getShouldCount() + " done=" + p.getDoneCount()
                    + " lack=" + p.getLackCount());
        }
    }
}
