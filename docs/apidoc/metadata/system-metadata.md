# 系统元数据说明

制品库运行过程中会为制品增加一些系统元数据，例如制品扫描状态，依赖包版本，包名等信息

## 制品分析产生的元数据

| key            | 类型      | 必须  | 取值范围                           | Node元数据 | Package元数据 | 说明                        | Description                  |
|----------------|---------|-----|--------------------------------|---------|------------|---------------------------|------------------------------|
| scanStatus     | string  | 否   | INIT,RUNNING,SUCCESS,FAILED    | 是       | 是          | 扫描状态,待扫描，扫描中，扫描成功，扫描失败    | scan status                  |
| qualityRedLine | boolean | 否   | true,false                     | 是       | 是          | 是否通过质量红线                  | pass quality red line or not |
| forbidStatus   | boolean | 否   | true,false                     | 是       | 是          | 是否被禁用                     | forbid status                |
| forbidUser     | string  | 否   | 无                              | 是       | 是          | 仅手动禁用时有值，表示谁禁用了该制品        | forbid user                  |
| forbidType     | string  | 否   | SCANNING,QUALITY_UNPASS,MANUAL | 是       | 是          | 禁用类型，分为扫描中，未通过质量红线，用户手动禁用 | forbid type                  |
