/**
 * 前端脱敏工具。**仅用于展示**，**不**作为安全边界。
 * 真正的脱敏 / 解密决策在后端做（design.md Decision 4），前端拿到的本就是脱敏后字符串；
 * 这里只提供少量补充展示规则，例如商户编号末 4 位、手机号 3-7 位。
 */

export function maskMerchantNo(no: string): string {
  if (!no || no.length <= 4) {
    return '****'
  }
  return '****' + no.slice(-4)
}

export function maskPhone(phone: string): string {
  if (!phone || phone.length < 7) {
    return '***'
  }
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

export function maskIdCard(id: string): string {
  if (!id || id.length < 8) {
    return '***'
  }
  return id.slice(0, 4) + '**********' + id.slice(-4)
}
