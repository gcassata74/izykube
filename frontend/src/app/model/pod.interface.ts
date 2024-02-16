import { Base } from "./base.interface";
import { Container} from "./container.interface";
import { Volume } from "./volume.interface";

export interface PodSpec extends Base {
    kind: "pod";
    containers: Container[];
    volumes?: Volume[];
  }
