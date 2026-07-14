// ICP备案相关类型定义

// 主体性质选项
export interface SubjectTypeOption {
  type: number;
  name: string;
}

// 地区选项
export interface RegionOption {
  code: number;
  name: string;
  subList?: RegionOption[];
}

// 证件类型选项
export interface CertificateTypeOption {
  type: number;
  name: string;
  subjectType: number;
}

// 服务类型选项
export interface ServiceTypeOption {
  type: number;
  name: string;
}

// 前置审批类型选项
export interface PreApprovalTypeOption {
  type: number;
  name: string;
}

// 前置审批项
export interface PreApprovalItem {
  type: number;
  code: string;
  media: string;
}

// 主体负责人信息
export interface PrincipalInfo {
  name: string;
  mobile: string;
  email: string;
  emergencyContact: string;
  certificateType?: number;
  certificateNumber: string;
  certificateValidityDateStart: string;
  certificateValidityDateEnd: string;
  certificatePhotoFront: string;
  certificatePhotoBack: string;
  authorizationLetter: string;
  verifyTaskId: string;
  isLegalPerson?: number;
}

// 主体信息
export interface SubjectJson {
  personalInfo: {
    residencePermit: string;
  };
  organizeInfo: {
    certificateAddress: string;
    certificateNumber: string;
    certificatePhoto: string;
    certificateType?: number;
  };
  principalInfo: PrincipalInfo;
  legalPersonInfo: {
    certificateNumber: string;
    name: string;
  };
}

// 小程序信息
export interface AppletsJson {
  nrlxDetails: PreApprovalItem[];
  principalInfo: PrincipalInfo;
}

// 材料信息
export interface MaterialsJson {
  commitmentLetter: string[];
  businessNameChangeLetter: string[];
  partyConstructionConfirmationLetter: string[];
  promiseVideo: string[];
  authenticityResponsibilityLetter: string[];
  authenticityCommitmentLetter: string[];
  websiteConstructionProposal: string[];
  subjectOtherMaterials: string[];
  appletsOtherMaterials: string[];
  holdingCertificatePhoto: string[];
}

// ICP表单数据
export interface IcpFormData {
  id: string;
  // 基础信息
  taskId: string;
  appId: string;
  subjectType?: number;
  subjectName: string;
  subjectProvince: string;
  subjectCity: string;
  subjectDistrict: string;
  regionSelection: number[];
  subjectAddress: string;
  subjectComment: string;
  subjectRecordNumber: string;
  usePrincipalInfo: string;

  // 小程序信息
  appletsAppid: string;
  appletsName: string;
  appletsServiceContentTypes: number[];
  appletsComment: string;
  appletsRecordNumber: string;

  // JSON字段
  subjectJson: SubjectJson;
  appletsJson: AppletsJson;
  materialsJson: MaterialsJson;

  // 状态信息
  faceStatus?: number;
  status?: number;
  smsVerifyStatus?: number;
  failReason: string;

  // 系统字段
  createBy: string;
  updateBy: string;
  createTime: string;
  updateTime: string;
  delFlag: string;

  // 临时字段
  authUrl: string;
  authMsg: string;
}

// 人脸核身任务信息
export interface FaceVerifyTask {
  taskId: string;
  faceStatus: number;
  authUrl: string;
  authMsg: string;
}

// 组件Props
export interface IcpTabProps {
  appId?: string;
  miniBaseInfo?: any;
}

// 子组件Props
export interface FaceVerifyProps {
  appId: string;
  taskId?: string;
  faceStatus?: number;
  authMsg?: string;
  onFaceVerifySuccess: () => void;
}

export interface SubjectInfoProps {
  formData: IcpFormData;
  subjectTypeOptions: SubjectTypeOption[];
  regionOptions: RegionOption[];
  certificateTypeOptions: CertificateTypeOption[];
  isDisabled: boolean;
  appId: string;
}

export interface PrincipalInfoProps {
  formData: IcpFormData;
  certificateTypeOptions: CertificateTypeOption[];
  isDisabled: boolean;
  isSubjectPrincipal?: boolean;
  subjectTypeOptions: SubjectTypeOption[];
  appId: string;
}

export interface AppletsInfoProps {
  formData: IcpFormData;
  serviceTypeOptions: ServiceTypeOption[];
  preApprovalTypeOptions: PreApprovalTypeOption[];
  isDisabled: boolean;
  appId: string;
}

export interface MaterialsProps {
  formData: IcpFormData;
  isDisabled: boolean;
  appId: string;
}

export interface UploadProps {
  modelValue?: string | string[];
  appId: string;
  limit: number;
  disabled?: boolean;
  type: string; // 媒体材料类型。目前支持两种：图片("image")和视频("video")，示例值："image"
  certificateType?: number; // 证件类型(参考：获取证件类型)，如果上传的是证件媒体材料，则必填，示例值：2
  icpOrderField: string; // 媒体材料所属的备案字段名(参考：申请小程序备案接口)，如要用于多个备案字段，则填写其中一个字段名即可。例如：要上传身份证头像面照片作为备案主体负责人和小程序负责人的证件照正面，就填写 "icp_subject.principal_info.certificate_photo_front"
}
