-- SetHub 第一次启动所需的基础资料。
-- 这份脚本只创建公司、办公地点和岗位，不创建假人才或招聘流程数据。
-- 每条 INSERT 都先检查目标数据是否存在，所以后端重复启动不会重复插入。

SET NAMES utf8mb4;

INSERT INTO company(org_id,name,active,created_at)
SELECT 1,'南通宣通文化投资',TRUE,DATE_FORMAT(NOW(),'%Y-%m-%dT%H:%i:%s+08:00')
WHERE NOT EXISTS (
    SELECT 1 FROM company WHERE org_id=1 AND name='南通宣通文化投资'
);

INSERT INTO company_location(org_id,company_id,base_city,district,street,office_address,active)
SELECT 1,c.id,'南通','崇川区','文峰街道','青年中路',TRUE
FROM company c
WHERE c.org_id=1 AND c.name='南通宣通文化投资'
  AND NOT EXISTS (
      SELECT 1 FROM company_location l
      WHERE l.company_id=c.id AND l.base_city='南通' AND l.district='崇川区'
        AND l.street='文峰街道' AND l.office_address='青年中路'
  );

INSERT INTO company_location(org_id,company_id,base_city,district,street,office_address,active)
SELECT 1,c.id,'南通','崇川区','新城桥街道','工农南路',TRUE
FROM company c
WHERE c.org_id=1 AND c.name='南通宣通文化投资'
  AND NOT EXISTS (
      SELECT 1 FROM company_location l
      WHERE l.company_id=c.id AND l.base_city='南通' AND l.district='崇川区'
        AND l.street='新城桥街道' AND l.office_address='工农南路'
  );

INSERT INTO company_location(org_id,company_id,base_city,district,street,office_address,active)
SELECT 1,c.id,'南通','崇川区','狼山镇街道','长江南路',TRUE
FROM company c
WHERE c.org_id=1 AND c.name='南通宣通文化投资'
  AND NOT EXISTS (
      SELECT 1 FROM company_location l
      WHERE l.company_id=c.id AND l.base_city='南通' AND l.district='崇川区'
        AND l.street='狼山镇街道' AND l.office_address='长江南路'
  );

INSERT INTO company_location(org_id,company_id,base_city,district,street,office_address,active)
SELECT 1,c.id,'南通','通州区','金沙街道','朝霞路',TRUE
FROM company c
WHERE c.org_id=1 AND c.name='南通宣通文化投资'
  AND NOT EXISTS (
      SELECT 1 FROM company_location l
      WHERE l.company_id=c.id AND l.base_city='南通' AND l.district='通州区'
        AND l.street='金沙街道' AND l.office_address='朝霞路'
  );

INSERT INTO company_location(org_id,company_id,base_city,district,street,office_address,active)
SELECT 1,c.id,'上海','浦东新区','陆家嘴街道','世纪大道',TRUE
FROM company c
WHERE c.org_id=1 AND c.name='南通宣通文化投资'
  AND NOT EXISTS (
      SELECT 1 FROM company_location l
      WHERE l.company_id=c.id AND l.base_city='上海' AND l.district='浦东新区'
        AND l.street='陆家嘴街道' AND l.office_address='世纪大道'
  );

INSERT INTO `position`(
    org_id,revision,parent_position_id,current_record,version_number,owner_employee_id,
    name,min_salary,max_salary,company_id,company,recruitment_code,department,location,
    base_location,description,status,version_note,headcount,employment_type,requirements,
    created_at,updated_at
)
SELECT
    1,1,NULL,TRUE,1,e.id,
    'Java后端开发工程师',18,28,c.id,c.name,'001','技术研发部',
    '南通 · 崇川区 · 文峰街道 · 青年中路','南通',
    '负责业务系统后端接口、数据库设计、服务稳定性和日常维护。',
    '招聘中','系统首次启动创建',2,'全职',
    '熟悉 Java、Spring Boot 和 MySQL，能够独立排查问题并与产品、前端协作。',
    DATE_FORMAT(NOW(),'%Y-%m-%dT%H:%i:%s+08:00'),DATE_FORMAT(NOW(),'%Y-%m-%dT%H:%i:%s+08:00')
FROM company c
JOIN employee e ON e.org_id=1 AND e.name='昱宁' AND e.role='HR' AND e.active=TRUE
WHERE c.org_id=1 AND c.name='南通宣通文化投资'
  AND NOT EXISTS (
      SELECT 1 FROM `position` p
      WHERE p.org_id=1 AND p.parent_position_id IS NULL AND p.recruitment_code='001'
  )
ORDER BY e.id
LIMIT 1;

INSERT INTO `position`(
    org_id,revision,parent_position_id,current_record,version_number,owner_employee_id,
    name,min_salary,max_salary,company_id,company,recruitment_code,department,location,
    base_location,description,status,version_note,headcount,employment_type,requirements,
    created_at,updated_at
)
SELECT
    1,1,NULL,TRUE,1,e.id,
    '品牌设计师',12,18,c.id,c.name,'002','品牌创意部',
    '南通 · 崇川区 · 文峰街道 · 青年中路','南通',
    '负责品牌视觉、活动物料、产品宣传设计和设计规范维护。',
    '招聘中','系统首次启动创建',1,'全职',
    '熟练使用主流设计工具，具备完整作品集、品牌意识和良好的沟通能力。',
    DATE_FORMAT(NOW(),'%Y-%m-%dT%H:%i:%s+08:00'),DATE_FORMAT(NOW(),'%Y-%m-%dT%H:%i:%s+08:00')
FROM company c
JOIN employee e ON e.org_id=1 AND e.name='昱宁' AND e.role='HR' AND e.active=TRUE
WHERE c.org_id=1 AND c.name='南通宣通文化投资'
  AND NOT EXISTS (
      SELECT 1 FROM `position` p
      WHERE p.org_id=1 AND p.parent_position_id IS NULL AND p.recruitment_code='002'
  )
ORDER BY e.id
LIMIT 1;

INSERT INTO `position`(
    org_id,revision,parent_position_id,current_record,version_number,owner_employee_id,
    name,min_salary,max_salary,company_id,company,recruitment_code,department,location,
    base_location,description,status,version_note,headcount,employment_type,requirements,
    created_at,updated_at
)
SELECT
    1,1,NULL,TRUE,1,e.id,
    '摄影师',10,16,c.id,c.name,'003','创意制作部',
    '上海 · 浦东新区 · 陆家嘴街道 · 世纪大道','上海',
    '负责人物、活动和宣传内容拍摄，并完成基础后期与素材归档。',
    '招聘中','系统首次启动创建',1,'全职',
    '能够独立完成拍摄方案、现场执行和后期交付，有人物或活动摄影作品。',
    DATE_FORMAT(NOW(),'%Y-%m-%dT%H:%i:%s+08:00'),DATE_FORMAT(NOW(),'%Y-%m-%dT%H:%i:%s+08:00')
FROM company c
JOIN employee e ON e.org_id=1 AND e.name='昱宁' AND e.role='HR' AND e.active=TRUE
WHERE c.org_id=1 AND c.name='南通宣通文化投资'
  AND NOT EXISTS (
      SELECT 1 FROM `position` p
      WHERE p.org_id=1 AND p.parent_position_id IS NULL AND p.recruitment_code='003'
  )
ORDER BY e.id
LIMIT 1;
