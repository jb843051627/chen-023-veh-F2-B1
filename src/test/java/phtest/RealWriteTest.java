package phtest;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.common.conf.V2Config;
import com.fc.v2.common.exception.VehBindRejectException;
import com.fc.v2.mapper.auto.TVehBindPageMapper;
import com.fc.v2.mapper.auto.TVehEntMapper;
import com.fc.v2.mapper.custom.TVehBindPageBoardMapper;
import com.fc.v2.model.auto.TVehBindPage;
import com.fc.v2.model.auto.TVehEnt;
import com.fc.v2.model.custom.VehBindForm;
import com.fc.v2.service.impl.TVehBindPageServiceImpl;
import org.apache.ibatis.reflection.MetaObject;
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
import java.util.Date;

@SpringBootTest(classes = RealWriteTest.Cfg.class)
class RealWriteTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({"com.fc.v2.mapper.auto", "com.fc.v2.mapper.custom"})
    static class Cfg {
        @Bean
        @Primary
        public DataSource dataSource() {
            return DataSourceBuilder.create()
                    .url("jdbc:h2:mem:write;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
                    .driverClassName("org.h2.Driver")
                    .username("sa").password("").build();
        }

        @Bean
        public DataSourceInitializer init(DataSource ds) {
            String sql =
                "CREATE TABLE t_veh_bind_page ("
                + "id BIGINT PRIMARY KEY, bill_no VARCHAR(64), sign_no VARCHAR(64), veh_code VARCHAR(64),"
                + "node_no INT, site_id INT, site_no VARCHAR(64), should_count INT, done_count INT,"
                + "lack_count INT, content VARCHAR(255), status INT, del_flag INT,"
                + "create_by VARCHAR(64), create_time TIMESTAMP, update_by VARCHAR(64), update_time TIMESTAMP,"
                + "remark VARCHAR(500));"
                + "CREATE TABLE t_veh_ent ("
                + "id BIGINT PRIMARY KEY, site_no VARCHAR(64), site_name VARCHAR(128),"
                + "site_type VARCHAR(32), road_name VARCHAR(128), status INT, del_flag INT,"
                + "create_by VARCHAR(64), create_time TIMESTAMP, update_by VARCHAR(64), update_time TIMESTAMP,"
                + "remark VARCHAR(500));"
                // 0 在册；1 已销户(=1)；2 状态为 NULL 的"停用/历史"业户；3 非标准停用码 9
                + "INSERT INTO t_veh_ent (id,site_no,site_name,site_type,road_name,status,del_flag) VALUES (0,'YH00','顺达','道路旅客运输业户','宁川省—宁州市—临江县',0,0);"
                + "INSERT INTO t_veh_ent (id,site_no,site_name,site_type,road_name,status,del_flag) VALUES (1,'YH01','通达','危险货物运输业户','宁川省—宁州市—临江县',1,0);"
                + "INSERT INTO t_veh_ent (id,site_no,site_name,site_type,road_name,status,del_flag) VALUES (2,'YH02','空态业户','普通货运业户','宁川省—宁州市—临江县',NULL,0);"
                + "INSERT INTO t_veh_ent (id,site_no,site_name,site_type,road_name,status,del_flag) VALUES (3,'YH03','九号停用','普通货运业户','宁川省—宁州市—临江县',9,0);";
            DataSourceInitializer i = new DataSourceInitializer();
            i.setDataSource(ds);
            i.setDatabasePopulator(new ResourceDatabasePopulator(
                    new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))));
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
            GlobalConfig gc = new GlobalConfig();
            // 模拟当前 MetaObjectHandler：署名取“当前登录名”
            gc.setMetaObjectHandler(new MetaObjectHandler() {
                @Override
                public void insertFill(MetaObject m) {
                    this.fillStrategy(m, "createBy", CURRENT_LOGIN);
                    this.strictInsertFill(m, "createTime", Date.class, new Date());
                }
                @Override
                public void updateFill(MetaObject m) {
                    this.fillStrategy(m, "updateBy", CURRENT_LOGIN);
                    this.strictUpdateFill(m, "updateTime", Date.class, new Date());
                }
            });
            fb.setGlobalConfig(gc);
            return fb.getObject();
        }
    }

    /** 模拟“当前登录账号” */
    static String CURRENT_LOGIN = "zhang.san";

    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    private TVehBindPageMapper pageMapper;
    private TVehEntMapper entMapper;
    private TVehBindPageBoardMapper boardMapper;
    private TVehBindPageServiceImpl service;

    @BeforeEach
    void wire() {
        org.apache.ibatis.session.SqlSession s = sqlSessionFactory.openSession(true);
        pageMapper = s.getMapper(TVehBindPageMapper.class);
        entMapper = s.getMapper(TVehEntMapper.class);
        boardMapper = s.getMapper(TVehBindPageBoardMapper.class);
        service = new TVehBindPageServiceImpl();
        V2Config cfg = new V2Config();
        cfg.setVehBindPageSize(3);
        ReflectionTestUtils.setField(service, "baseMapper", pageMapper);
        ReflectionTestUtils.setField(service, "vehEntMapper", entMapper);
        ReflectionTestUtils.setField(service, "boardMapper", boardMapper);
        ReflectionTestUtils.setField(service, "v2Config", cfg);
    }

    private VehBindForm form(Integer siteId, String siteNo, String veh, String should, String done) {
        VehBindForm f = new VehBindForm();
        f.setSiteId(siteId);
        f.setSiteNo(siteNo);
        f.setVehCode(veh);
        f.setNodeNo(0);
        f.setShouldCountRaw(should);
        f.setDoneCountRaw(done);
        return f;
    }

    @Test
    void t01_insertOk_lackAndStatusAndSign() {
        int r = service.saveFromForm(form(0, "YH00", "VC-A", "10", "4"));
        TVehBindPage p = pageMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TVehBindPage>().eq("veh_code", "VC-A"));
        System.out.println(">>> 新增成功 r=" + r + " billNo=" + p.getBillNo()
                + " lack=" + p.getLackCount() + " status=" + p.getStatus()
                + " createBy=" + p.getCreateBy());
    }

    @Test
    void t02_decimalShould() {
        try {
            service.saveFromForm(form(0, "YH00", "VC-DEC", "10.12345", "4"));
            TVehBindPage p = pageMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TVehBindPage>().eq("veh_code", "VC-DEC"));
            System.out.println(">>> 小数应联竟然落库! should=" + (p == null ? "null" : p.getShouldCount())
                    + " 原始是否被截断");
        } catch (VehBindRejectException e) {
            System.out.println(">>> 小数应联被拒: [" + e.getField() + "] " + e.getMessage());
        }
    }

    @Test
    void t03_closedEnt_status1() {
        try {
            service.saveFromForm(form(1, "YH01", "VC-C1", "10", "4"));
            System.out.println(">>> 销户(=1)业户竟然可保存!");
        } catch (VehBindRejectException e) {
            System.out.println(">>> 销户(=1)业户被拒: [" + e.getField() + "] " + e.getMessage());
        }
    }

    @Test
    void t04_nullStatusEnt() {
        try {
            service.saveFromForm(form(2, "YH02", "VC-NULL", "10", "4"));
            System.out.println(">>> status=NULL 业户竟然可保存! (白/黑名单口径不齐的洞)");
        } catch (VehBindRejectException e) {
            System.out.println(">>> status=NULL 业户被拒: [" + e.getField() + "] " + e.getMessage());
        }
    }

    @Test
    void t05_status9Ent() {
        try {
            service.saveFromForm(form(3, "YH03", "VC-9", "10", "4"));
            System.out.println(">>> status=9 停用业户竟然可保存! (白/黑名单口径不齐的洞)");
        } catch (VehBindRejectException e) {
            System.out.println(">>> status=9 停用业户被拒: [" + e.getField() + "] " + e.getMessage());
        }
    }

    @Test
    void t06_dupVehCode() {
        service.saveFromForm(form(0, "YH00", "VC-DUP", "10", "4"));
        try {
            service.saveFromForm(form(0, "YH00", "VC-DUP", "10", "4"));
            System.out.println(">>> 同车代号第二张竟然立成! (查重只管 del_flag=0 的半截?)");
        } catch (VehBindRejectException e) {
            System.out.println(">>> 同车代号被拒: [" + e.getField() + "] " + e.getMessage());
        }
    }

    @Test
    void t07_editWithoutChangingCode() {
        service.saveFromForm(form(0, "YH00", "VC-EDIT", "10", "4"));
        TVehBindPage p = pageMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TVehBindPage>().eq("veh_code", "VC-EDIT"));
        VehBindForm f = form(null, null, null, "10", "8");
        f.setId(p.getId());
        // nodeNo 仍要给（编辑校验层级）
        f.setNodeNo(0);
        try {
            int r = service.saveFromForm(f);
            TVehBindPage after = service.selectTVehBindPageById(p.getId());
            System.out.println(">>> 编辑(代号不动) r=" + r + " done=" + after.getDoneCount()
                    + " lack=" + after.getLackCount() + " status=" + after.getStatus());
        } catch (VehBindRejectException e) {
            System.out.println(">>> 编辑(代号不动)被拒! [" + e.getField() + "] " + e.getMessage());
        }
    }

    @Test
    void t08_balanceDirection_whenDoneIncreases() {
        // 已联越多，还差应越少
        service.saveFromForm(form(0, "YH00", "VC-BAL", "10", "2"));
        TVehBindPage p = pageMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TVehBindPage>().eq("veh_code", "VC-BAL"));
        System.out.println(">>> 应联10 已联2 -> lack=" + p.getLackCount() + " (应为8; 若=已联2 或反向即错)");
    }

    @Test
    void t09_siteNoMismatch() {
        try {
            service.saveFromForm(form(0, "ZZ99", "VC-NO", "10", "4"));
            System.out.println(">>> 业户代号与名录不一致竟然保存!");
        } catch (VehBindRejectException e) {
            System.out.println(">>> 业户代号不符被拒: [" + e.getField() + "] " + e.getMessage());
        }
    }
}
