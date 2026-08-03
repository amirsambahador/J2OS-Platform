export const useClass = () => {
  class Amirsam {
    static AGE = 40
    #name
    #family

    constructor(name, family) {
      this.#name = name
      this.#family = family
      /*
      در این مثال نام و فامیل private تعریف شده است (با استفاده از #)
      ولی جون برنامه نویس می تواند از بیرون بنویسد amir.name و این name به کلاس اضافه می شود با استفاده از متد seal این کلاس را بسته بندی میکنیم
       */
      Object.seal(this)
    }

    setName(name) {
      this.#name = name
    }

    getName() {
      return this.#name
    }

    setFamily(family) {
      this.#family = family
    }

    getFamily() {
      return this.#family
    }

    m1() {
      alert('This is m1 from Amirsam class')
    }

    static m2() {
      alert('This is m2 from Amirsam class')
    }
  }

  return {
    Amirsam
  }
}
